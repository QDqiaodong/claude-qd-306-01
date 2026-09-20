package com.print.shop.service;

import com.print.shop.dto.DelayBoardEntry;
import com.print.shop.entity.Paper;
import com.print.shop.entity.Plate;
import com.print.shop.entity.PrintJob;
import com.print.shop.repository.PaperRepository;
import com.print.shop.repository.PlateRepository;
import com.print.shop.repository.PrintJobRepository;
import com.print.shop.spec.PrintJobSpecs;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 延误桌：把交期、纸、版三件事摊到同一张桌上给调度看。
 *
 * 桌上没有自己的数据，每次看桌都按眼下的工单、纸张、印版重算：
 *  - 改交期：天数和加急跟着新日子走；改回未来，这条当场从桌上拿掉；
 *  - 纸补进货、版修好：只因纸或版升上去的加急，自己退回普通延误。
 * 所以这里没有「刷新延误」的写接口，仓管改纸、机长改版、调度改交期，
 * 各改各的台账，桌上读到的永远是最新那一笔。
 */
@Service
public class DelayBoardService {

    /** 超期超过这个天数就升加急（不含第 3 天，第 4 天起）。 */
    static final int URGENT_AFTER_DAYS = 3;

    private final PrintJobRepository jobs;
    private final PaperRepository papers;
    private final PlateRepository plates;

    public DelayBoardService(PrintJobRepository jobs, PaperRepository papers, PlateRepository plates) {
        this.jobs = jobs;
        this.papers = papers;
        this.plates = plates;
    }

    public List<DelayBoardEntry> board() {
        return board(LocalDate.now());
    }

    /** 按「今天」是哪一天算一桌（单独开个口，好让验收用例把日子钉死）。 */
    public List<DelayBoardEntry> board(LocalDate today) {
        List<PrintJob> overdue = jobs.findAll(PrintJobSpecs.overdueUnfinished(today));
        if (overdue.isEmpty()) {
            return List.of();
        }
        Map<Long, Paper> paperById = papers.findAll().stream()
                .collect(Collectors.toMap(p -> p.id, Function.identity()));
        Map<Long, Plate> plateById = plates.findAll().stream()
                .collect(Collectors.toMap(p -> p.id, Function.identity()));

        List<DelayBoardEntry> rows = new ArrayList<>();
        for (PrintJob j : overdue) {
            rows.add(entryOf(j, today, paperById, plateById));
        }
        // 加急的压桌面的最上头；同急同缓，谁超得久谁靠前。
        rows.sort(Comparator.comparing((DelayBoardEntry e) -> e.urgent).reversed()
                .thenComparing(Comparator.comparingLong((DelayBoardEntry e) -> e.overdueDays).reversed())
                .thenComparing(e -> e.jobId));
        return rows;
    }

    private DelayBoardEntry entryOf(PrintJob j, LocalDate today,
                                   Map<Long, Paper> paperById, Map<Long, Plate> plateById) {
        DelayBoardEntry e = new DelayBoardEntry();
        e.jobId = j.id;
        e.jobNo = j.jobNo;
        e.clientName = j.clientName;
        e.jobState = j.jobState;
        e.copies = j.copies;
        e.dueDate = j.dueDate;
        e.overdueDays = ChronoUnit.DAYS.between(j.dueDate, today);

        Paper paper = j.paperId == null ? null : paperById.get(j.paperId);
        Plate plate = j.plateId == null ? null : plateById.get(j.plateId);
        e.paperOut = paper != null && "缺货".equals(paper.paperState);
        e.plateWorn = plate != null && "已磨损".equals(plate.plateState);

        // 加急两条路：日子超得太多，或者纸/版眼下正拖着——哪怕刚超一天。
        e.urgent = e.overdueDays > URGENT_AFTER_DAYS || e.paperOut || e.plateWorn;
        e.dragBy = e.paperOut && e.plateWorn ? "纸+版" : e.paperOut ? "纸" : e.plateWorn ? "版" : null;

        if (paper != null) {
            e.paperId = paper.id;
            e.paperName = paper.paperName;
            e.paperState = paper.paperState;
        }
        if (plate != null) {
            e.plateId = plate.id;
            e.plateCode = plate.plateCode;
            e.plateState = plate.plateState;
        }
        return e;
    }
}
