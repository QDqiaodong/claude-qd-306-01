package com.print.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.print.shop.dto.DelayBoardEntry;
import com.print.shop.entity.Paper;
import com.print.shop.entity.Plate;
import com.print.shop.entity.PrintJob;
import com.print.shop.repository.PaperRepository;
import com.print.shop.repository.PlateRepository;
import com.print.shop.repository.PrintJobRepository;
import com.print.shop.service.DelayBoardService;
import com.print.shop.service.PrintJobService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 延误桌的验收用例：谁上桌、谁加急、谁拖的、改完跟着动。
 * 日子钉在 TODAY，直接打 service 层——桌上的每一行都是按眼下台账算出来的。
 */
@SpringBootTest
class DelayBoardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

    @Autowired
    private DelayBoardService board;
    @Autowired
    private PrintJobService jobService;
    @Autowired
    private PrintJobRepository jobRepo;
    @Autowired
    private PaperRepository paperRepo;
    @Autowired
    private PlateRepository plateRepo;

    private Paper okPaper;
    private Paper outPaper;
    private Plate okPlate;
    private Plate wornPlate;

    @BeforeEach
    void setUp() {
        jobRepo.deleteAll();
        paperRepo.deleteAll();
        plateRepo.deleteAll();

        okPaper = paper("PP-T1", "充足");
        outPaper = paper("PP-T2", "缺货");
        okPlate = plate("PL-T1", "在用");
        wornPlate = plate("PL-T2", "已磨损");
    }

    private Paper paper(String code, String state) {
        Paper p = new Paper();
        p.paperCode = code;
        p.paperName = code + " 纸";
        p.stock = 1000;
        p.paperState = state;
        return paperRepo.save(p);
    }

    private Plate plate(String code, String state) {
        Plate p = new Plate();
        p.plateCode = code;
        p.plateName = code + " 版";
        p.plateState = state;
        return plateRepo.save(p);
    }

    private PrintJob job(String no, String state, LocalDate due, Paper paper, Plate plate) {
        PrintJob j = new PrintJob();
        j.jobNo = no;
        j.clientName = "客户" + no;
        j.jobState = state;
        j.dueDate = due;
        j.copies = 1000;
        j.paperId = paper == null ? null : paper.id;
        j.plateId = plate == null ? null : plate.id;
        return jobRepo.save(j);
    }

    private DelayBoardEntry rowOf(List<DelayBoardEntry> rows, String jobNo) {
        return rows.stream().filter(r -> r.jobNo.equals(jobNo)).findFirst().orElse(null);
    }

    // —— 谁上桌 ——

    @Test
    void 过交期的待印和印刷中上桌_已完成和未来交期不上桌() {
        job("PJ-D1", "待印", TODAY.minusDays(1), okPaper, okPlate);
        job("PJ-D2", "印刷中", TODAY.minusDays(2), okPaper, okPlate);
        job("PJ-D3", "已完成", TODAY.minusDays(5), okPaper, okPlate);
        job("PJ-D4", "待印", TODAY.plusDays(3), okPaper, okPlate);
        job("PJ-D5", "待印", TODAY, okPaper, okPlate); // 交期是今天，还没过
        job("PJ-D6", "待印", null, okPaper, okPlate);  // 没写交期

        List<DelayBoardEntry> rows = board.board(TODAY);
        List<String> onTable = rows.stream().map(r -> r.jobNo).sorted().toList();
        assertEquals(List.of("PJ-D1", "PJ-D2"), onTable);
        assertEquals(1, rowOf(rows, "PJ-D1").overdueDays);
        assertEquals(2, rowOf(rows, "PJ-D2").overdueDays);
    }

    // —— 超期天数升加急 ——

    @Test
    void 超期三天以内普通_第四天起加急() {
        job("PJ-E1", "待印", TODAY.minusDays(1), okPaper, okPlate);
        job("PJ-E3", "待印", TODAY.minusDays(3), okPaper, okPlate);
        job("PJ-E4", "待印", TODAY.minusDays(4), okPaper, okPlate);

        List<DelayBoardEntry> rows = board.board(TODAY);
        assertFalse(rowOf(rows, "PJ-E1").urgent);
        assertFalse(rowOf(rows, "PJ-E3").urgent);
        assertTrue(rowOf(rows, "PJ-E4").urgent);
        assertNull(rowOf(rows, "PJ-E4").dragBy); // 纯超期，不赖纸也不赖版
    }

    // —— 纸和版把刚超期的单顶成加急 ——

    @Test
    void 纸缺货时刚超一天也加急_桌上写明是纸拖的() {
        job("PJ-P1", "待印", TODAY.minusDays(1), outPaper, okPlate);
        List<DelayBoardEntry> rows = board.board(TODAY);
        DelayBoardEntry e = rowOf(rows, "PJ-P1");
        assertTrue(e.urgent);
        assertEquals("纸", e.dragBy);
        assertTrue(e.paperOut);
        assertFalse(e.plateWorn);
        assertEquals("缺货", e.paperState);
    }

    @Test
    void 版已磨损时刚超一天也加急_桌上写明是版拖的() {
        job("PJ-G1", "待印", TODAY.minusDays(1), okPaper, wornPlate);
        List<DelayBoardEntry> rows = board.board(TODAY);
        DelayBoardEntry e = rowOf(rows, "PJ-G1");
        assertTrue(e.urgent);
        assertEquals("版", e.dragBy);
        assertTrue(e.plateWorn);
        assertEquals("已磨损", e.plateState);
    }

    @Test
    void 纸和版都拖时写明纸加版() {
        job("PJ-B1", "待印", TODAY.minusDays(1), outPaper, wornPlate);
        DelayBoardEntry e = rowOf(board.board(TODAY), "PJ-B1");
        assertTrue(e.urgent);
        assertEquals("纸+版", e.dragBy);
    }

    // —— 改交期，桌上跟着走 ——

    @Test
    void 改交期后天数和加急跟着新日子走() {
        PrintJob j = job("PJ-C1", "待印", TODAY.minusDays(1), okPaper, okPlate);
        assertFalse(rowOf(board.board(TODAY), "PJ-C1").urgent);

        // 交期往前改到五天前：超 5 天，升加急
        PrintJob form = new PrintJob();
        form.id = j.id;
        form.dueDate = TODAY.minusDays(5);
        jobService.save(form);
        DelayBoardEntry e = rowOf(board.board(TODAY), "PJ-C1");
        assertEquals(5, e.overdueDays);
        assertTrue(e.urgent);
    }

    @Test
    void 交期改回未来这条从桌上拿掉() {
        PrintJob j = job("PJ-C2", "待印", TODAY.minusDays(2), okPaper, okPlate);
        assertEquals(1, board.board(TODAY).size());

        PrintJob form = new PrintJob();
        form.id = j.id;
        form.dueDate = TODAY.plusDays(7);
        jobService.save(form);
        assertTrue(board.board(TODAY).isEmpty());
    }

    // —— 纸补货、版修好，加急不钉死 ——

    @Test
    void 纸补进货后只因纸引起的加急退回普通延误() {
        job("PJ-R1", "待印", TODAY.minusDays(1), outPaper, okPlate);
        assertTrue(rowOf(board.board(TODAY), "PJ-R1").urgent);

        Paper restocked = paperRepo.findById(outPaper.id).orElseThrow();
        restocked.paperState = "充足";
        restocked.stock = 9000;
        paperRepo.save(restocked);

        DelayBoardEntry e = rowOf(board.board(TODAY), "PJ-R1");
        assertFalse(e.urgent);   // 退回普通延误，但人还在桌上（超期事实没变）
        assertNull(e.dragBy);
        assertEquals(1, e.overdueDays);
    }

    @Test
    void 版修好后只因版引起的加急退回普通延误() {
        job("PJ-R2", "待印", TODAY.minusDays(2), okPaper, wornPlate);
        assertTrue(rowOf(board.board(TODAY), "PJ-R2").urgent);

        Plate fixed = plateRepo.findById(wornPlate.id).orElseThrow();
        fixed.plateState = "在用";
        plateRepo.save(fixed);

        DelayBoardEntry e = rowOf(board.board(TODAY), "PJ-R2");
        assertFalse(e.urgent);
        assertNull(e.dragBy);
    }

    @Test
    void 超期天数本身够加急时_补纸修版也降不下来() {
        // 超 5 天 + 纸缺货：纸补上了，天数还在，加急不撤
        job("PJ-R3", "待印", TODAY.minusDays(5), outPaper, okPlate);
        assertTrue(rowOf(board.board(TODAY), "PJ-R3").urgent);

        Paper restocked = paperRepo.findById(outPaper.id).orElseThrow();
        restocked.paperState = "充足";
        paperRepo.save(restocked);

        DelayBoardEntry e = rowOf(board.board(TODAY), "PJ-R3");
        assertTrue(e.urgent);
        assertNull(e.dragBy); // 但「纸拖的」这顶帽子要摘掉
    }

    // —— 桌面排序：加急压上面，同级谁超得久谁靠前 ——

    @Test
    void 加急的排在前面_同级按超期天数从多到少() {
        job("PJ-S1", "待印", TODAY.minusDays(2), okPaper, okPlate);   // 普通 2 天
        job("PJ-S2", "待印", TODAY.minusDays(6), okPaper, okPlate);   // 加急 6 天
        job("PJ-S3", "待印", TODAY.minusDays(1), outPaper, okPlate);  // 加急 1 天（纸拖）

        List<String> order = board.board(TODAY).stream().map(r -> r.jobNo).toList();
        assertEquals(List.of("PJ-S2", "PJ-S3", "PJ-S1"), order);
    }
}
