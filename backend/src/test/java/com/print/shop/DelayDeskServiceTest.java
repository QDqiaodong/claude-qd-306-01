package com.print.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.print.shop.dto.DelayDeskRow;
import com.print.shop.entity.Paper;
import com.print.shop.entity.Plate;
import com.print.shop.entity.PrintJob;
import com.print.shop.repository.PaperRepository;
import com.print.shop.repository.PlateRepository;
import com.print.shop.repository.PrintJobRepository;
import com.print.shop.service.DelayDeskService;
import com.print.shop.service.PrintJobService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 延误桌验收：谁进桌、谁加急、纸拖还是版拖，全部按当天 + 眼下纸版状态现算。
 * 用固定的「今天」(today) 落数据，天数和加急才钉得住。
 */
@SpringBootTest
class DelayDeskServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

    @Autowired
    private DelayDeskService desk;
    @Autowired
    private PrintJobService jobService;
    @Autowired
    private PrintJobRepository jobRepo;
    @Autowired
    private PaperRepository paperRepo;
    @Autowired
    private PlateRepository plateRepo;

    private Paper finePaper;
    private Paper outPaper;
    private Plate goodPlate;
    private Plate wornPlate;

    @BeforeEach
    void setUp() {
        jobRepo.deleteAll();
        paperRepo.deleteAll();
        plateRepo.deleteAll();

        finePaper = paper("PP-D1", "铜版纸", "充足");
        outPaper = paper("PP-D2", "牛皮纸", "缺货");
        goodPlate = plate("PL-D1", "画册封面版", "在用");
        wornPlate = plate("PL-D2", "说明书版", "已磨损");
    }

    private Paper paper(String code, String name, String state) {
        Paper p = new Paper();
        p.paperCode = code;
        p.paperName = name;
        p.stock = state.equals("缺货") ? 0 : 30000;
        p.paperState = state;
        return paperRepo.save(p);
    }

    private Plate plate(String code, String name, String state) {
        Plate p = new Plate();
        p.plateCode = code;
        p.plateName = name;
        p.plateState = state;
        return plateRepo.save(p);
    }

    private PrintJob job(String no, String state, Paper paper, Plate plate, LocalDate due) {
        PrintJob j = new PrintJob();
        j.jobNo = no;
        j.clientName = "测试客户";
        j.paperId = paper == null ? null : paper.id;
        j.plateId = plate == null ? null : plate.id;
        j.copies = 1000;
        j.dueDate = due;
        j.jobState = state;
        return jobRepo.save(j);
    }

    private PrintJob job(String no, String state, Paper paper, Plate plate, int overdueDays) {
        return job(no, state, paper, plate, TODAY.minusDays(overdueDays));
    }

    private DelayDeskRow row(List<DelayDeskRow> rows, String no) {
        return rows.stream().filter(r -> r.jobNo.equals(no)).findFirst().orElseThrow();
    }

    private long count(List<DelayDeskRow> rows, String no) {
        return rows.stream().filter(r -> r.jobNo.equals(no)).count();
    }

    // —— 谁进桌 ——

    @Test
    void 过了交期当天的待印和印刷中才进桌_交期当天和未来的不进() {
        job("J-01", "待印", finePaper, goodPlate, 1);
        job("J-02", "印刷中", finePaper, goodPlate, 2);
        job("J-03", "待印", finePaper, goodPlate, TODAY);   // 交期就是今天，还不算过
        job("J-04", "待印", finePaper, goodPlate, TODAY.plusDays(3));
        List<DelayDeskRow> rows = desk.listAt(TODAY);
        assertEquals(2, rows.size());
        assertEquals(1, count(rows, "J-01"));
        assertEquals(1, count(rows, "J-02"));
        assertEquals(0, count(rows, "J-03"));
        assertEquals(0, count(rows, "J-04"));
        assertEquals(1, row(rows, "J-01").overdueDays);
    }

    @Test
    void 已完成的哪怕早过交期也不进桌() {
        job("J-10", "已完成", finePaper, goodPlate, 30);
        assertTrue(desk.listAt(TODAY).isEmpty());
    }

    // —— 加急：逾期超过三天 ——

    @Test
    void 逾期三天内普通延误_第四天起加急() {
        job("J-20", "待印", finePaper, goodPlate, 3);
        job("J-21", "待印", finePaper, goodPlate, 4);
        List<DelayDeskRow> rows = desk.listAt(TODAY);
        assertFalse(row(rows, "J-20").urgent);
        assertTrue(row(rows, "J-21").urgent);
        assertTrue(row(rows, "J-21").reason.contains("超过 3 天"));
    }

    // —— 加急：纸 / 版，哪怕只逾期一天 ——

    @Test
    void 指定纸缺货只逾期一天也加急并写明纸拖的() {
        job("J-30", "待印", outPaper, goodPlate, 1);
        DelayDeskRow r = row(desk.listAt(TODAY), "J-30");
        assertTrue(r.urgent);
        assertTrue(r.paperBlocked);
        assertFalse(r.plateBlocked);
        assertTrue(r.reason.contains("纸拖的"));
        assertTrue(r.reason.contains("缺货"));
    }

    @Test
    void 指定版已磨损只逾期一天也加急并写明版拖的() {
        job("J-40", "待印", finePaper, wornPlate, 1);
        DelayDeskRow r = row(desk.listAt(TODAY), "J-40");
        assertTrue(r.urgent);
        assertFalse(r.paperBlocked);
        assertTrue(r.plateBlocked);
        assertTrue(r.reason.contains("版拖的"));
        assertTrue(r.reason.contains("已磨损"));
    }

    @Test
    void 纸和版同时出问题就两件事都写清() {
        job("J-50", "印刷中", outPaper, wornPlate, 1);
        DelayDeskRow r = row(desk.listAt(TODAY), "J-50");
        assertTrue(r.urgent);
        assertTrue(r.reason.contains("纸拖的"));
        assertTrue(r.reason.contains("版拖的"));
    }

    @Test
    void 纸只是紧张不算纸拖_版作废不算版拖() {
        Paper tight = paper("PP-D3", "双胶纸", "紧张");
        Plate dead = plate("PL-D3", "旧海报版", "已作废");
        job("J-60", "待印", tight, dead, 1);
        DelayDeskRow r = row(desk.listAt(TODAY), "J-60");
        assertFalse(r.paperBlocked);
        assertFalse(r.plateBlocked);
        assertFalse(r.urgent, "只逾期一天、纸紧张/版作废都不构成加急");
    }

    // —— 改交期 ——

    @Test
    void 改交期后桌上天数跟着新日子走_改回未来就离桌() {
        PrintJob j = job("J-70", "待印", finePaper, goodPlate, 6);
        DelayDeskRow before = row(desk.listAt(TODAY), "J-70");
        assertEquals(6, before.overdueDays);
        assertTrue(before.urgent);

        // 改成逾期 2 天：天数变 2，加急撤掉
        PrintJob form = new PrintJob();
        form.id = j.id;
        form.dueDate = TODAY.minusDays(2);
        jobService.save(form);
        DelayDeskRow shortened = row(desk.listAt(TODAY), "J-70");
        assertEquals(2, shortened.overdueDays);
        assertFalse(shortened.urgent);

        // 再改回未来：从桌上拿掉
        PrintJob future = new PrintJob();
        future.id = j.id;
        future.dueDate = TODAY.plusDays(5);
        jobService.save(future);
        assertEquals(0, count(desk.listAt(TODAY), "J-70"));
    }

    // —— 补纸、修版：加急不钉死 ——

    @Test
    void 纸补进货后只因纸加急的退回普通延误() {
        PrintJob j = job("J-80", "待印", outPaper, goodPlate, 1);
        assertTrue(row(desk.listAt(TODAY), "J-80").urgent);

        Paper restocked = paperRepo.findById(outPaper.id).orElseThrow();
        restocked.stock = 20000;
        restocked.paperState = "充足";
        paperRepo.save(restocked);

        DelayDeskRow r = row(desk.listAt(TODAY), "J-80");
        assertFalse(r.paperBlocked);
        assertFalse(r.urgent, "逾期一天又没超三天，纸补上了就该退回普通延误");
        assertFalse(r.reason.contains("纸拖的"));
    }

    @Test
    void 版修好后只因版加急的退回普通延误() {
        PrintJob j = job("J-90", "待印", finePaper, wornPlate, 1);
        assertTrue(row(desk.listAt(TODAY), "J-90").urgent);

        Plate fixed = plateRepo.findById(wornPlate.id).orElseThrow();
        fixed.plateState = "在用";
        plateRepo.save(fixed);

        DelayDeskRow r = row(desk.listAt(TODAY), "J-90");
        assertFalse(r.plateBlocked);
        assertFalse(r.urgent);
        assertFalse(r.reason.contains("版拖的"));
    }

    @Test
    void 纸补上但逾期已超三天加急仍然留着() {
        job("J-91", "待印", outPaper, goodPlate, 8);
        Paper restocked = paperRepo.findById(outPaper.id).orElseThrow();
        restocked.stock = 20000;
        restocked.paperState = "充足";
        paperRepo.save(restocked);

        DelayDeskRow r = row(desk.listAt(TODAY), "J-91");
        assertFalse(r.paperBlocked);
        assertTrue(r.urgent, "纸的加急撤了，但逾期 8 天本身就够加急");
        assertTrue(r.reason.contains("超过 3 天"));
        assertFalse(r.reason.contains("纸拖的"));
    }

    // —— 排序：加急压顶，其余逾期多的在前 ——

    @Test
    void 加急的排在最前_其余按逾期天数降序() {
        job("S-01", "待印", finePaper, goodPlate, 2);
        job("S-02", "待印", outPaper, goodPlate, 1);
        job("S-03", "待印", finePaper, goodPlate, 6);
        job("S-04", "待印", finePaper, wornPlate, 1);
        List<DelayDeskRow> rows = desk.listAt(TODAY);
        assertEquals(4, rows.size());
        assertTrue(rows.get(0).urgent);
        assertTrue(rows.get(1).urgent);
        assertTrue(rows.get(2).urgent);
        assertFalse(rows.get(3).urgent);
        assertEquals("S-03", rows.get(0).jobNo);   // 逾期 6 天的加急排最前
        assertEquals(2, rows.get(3).overdueDays);   // 唯一的普通延误沉底
    }
}
