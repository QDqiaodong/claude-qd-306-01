package com.print.shop.service;

import com.print.shop.dto.DelayDeskRow;
import com.print.shop.entity.Paper;
import com.print.shop.entity.Plate;
import com.print.shop.entity.PrintJob;
import com.print.shop.repository.PaperRepository;
import com.print.shop.repository.PlateRepository;
import com.print.shop.repository.PrintJobRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 延误桌：把交期、纸、版三件事摊到同一张桌上现算，不存快照。
 *
 * 进桌：交期已经过去（dueDate &lt; 今天）且单子还停在「待印 / 印刷中」。
 * 已完成的不进；交期被改回未来的当场离桌。
 *
 * 加急：逾期超过 {@link #URGENT_OVERDUE_DAYS} 天；
 * 或者指定的纸正缺货、指定的版正已磨损——哪怕只逾期一天也加急，并写清是谁拖的。
 * 纸后来补上、版后来修好，下一次拉桌就退回普通延误，加急不钉死。
 */
@Service
public class DelayDeskService {

    /** 逾期超过这一天数（不含）标加急：4 天起加急。 */
    public static final int URGENT_OVERDUE_DAYS = 3;

    private static final List<String> ACTIVE_STATES = List.of("待印", "印刷中");

    private final PrintJobRepository jobs;
    private final PaperRepository papers;
    private final PlateRepository plates;

    public DelayDeskService(PrintJobRepository jobs, PaperRepository papers, PlateRepository plates) {
        this.jobs = jobs;
        this.papers = papers;
        this.plates = plates;
    }

    /** 拉延误桌：按眼下的交期、纸张、印版状态现算。 */
    public List<DelayDeskRow> list() {
        return listAt(LocalDate.now());
    }

    /** 按指定「今天」算，便于验收用例钉住日期。 */
    public List<DelayDeskRow> listAt(LocalDate today) {
        List<PrintJob> active = jobs.findAll().stream()
                .filter(j -> j.dueDate != null)
                .filter(j -> ACTIVE_STATES.contains(j.jobState))
                .filter(j -> j.dueDate.isBefore(today))
                .toList();
        if (active.isEmpty()) {
            return List.of();
        }
        Map<Long, Paper> paperById = papers.findAll().stream()
                .collect(Collectors.toMap(p -> p.id, Function.identity()));
        Map<Long, Plate> plateById = plates.findAll().stream()
                .collect(Collectors.toMap(p -> p.id, Function.identity()));

        List<DelayDeskRow> rows = new ArrayList<>();
        for (PrintJob j : active) {
            rows.add(toRow(j, today, paperById, plateById));
        }
        // 加急的压在最上面，其余按逾期天数从多到少，天数相同的交期早的先来。
        rows.sort(Comparator
                .comparing((DelayDeskRow r) -> r.urgent).reversed()
                .thenComparing(Comparator.comparingInt((DelayDeskRow r) -> r.overdueDays).reversed())
                .thenComparing(r -> r.dueDate));
        return rows;
    }

    private DelayDeskRow toRow(PrintJob j, LocalDate today,
                               Map<Long, Paper> paperById, Map<Long, Plate> plateById) {
        DelayDeskRow r = new DelayDeskRow();
        r.id = j.id;
        r.jobNo = j.jobNo;
        r.clientName = j.clientName;
        r.jobState = j.jobState;
        r.copies = j.copies;
        r.dueDate = j.dueDate;
        r.overdueDays = (int) java.time.temporal.ChronoUnit.DAYS.between(j.dueDate, today);

        Paper paper = j.paperId == null ? null : paperById.get(j.paperId);
        if (paper != null) {
            r.paperId = paper.id;
            r.paperCode = paper.paperCode;
            r.paperName = paper.paperName;
            r.paperState = paper.paperState;
            r.paperBlocked = "缺货".equals(paper.paperState);
        }

        Plate plate = j.plateId == null ? null : plateById.get(j.plateId);
        if (plate != null) {
            r.plateId = plate.id;
            r.plateCode = plate.plateCode;
            r.plateName = plate.plateName;
            r.plateState = plate.plateState;
            // 延误桌只把「已磨损」当版拖：磨损还能修，作废是另一码事（开单时已经拦过）。
            r.plateBlocked = "已磨损".equals(plate.plateState);
        }

        r.urgent = r.overdueDays > URGENT_OVERDUE_DAYS || r.paperBlocked || r.plateBlocked;
        r.reason = buildReason(r);
        return r;
    }

    /** 一句话点清这条为什么在桌上、为什么加急：逾期天数，以及是纸拖的还是版拖的。 */
    private String buildReason(DelayDeskRow r) {
        StringBuilder sb = new StringBuilder("逾期 " + r.overdueDays + " 天");
        List<String> blockers = new ArrayList<>();
        if (r.paperBlocked) {
            blockers.add("纸拖的：用纸「" + r.paperName + "」(" + r.paperCode + ")缺货");
        }
        if (r.plateBlocked) {
            blockers.add("版拖的：印版 " + r.plateCode + "「" + r.plateName + "」已磨损");
        }
        if (r.urgent) {
            if (!blockers.isEmpty()) {
                sb.append("，加急（").append(String.join("；", blockers)).append("）");
            } else {
                sb.append("，加急（超过 ").append(URGENT_OVERDUE_DAYS).append(" 天）");
            }
        }
        return sb.toString();
    }
}
