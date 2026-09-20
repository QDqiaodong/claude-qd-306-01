package com.print.shop.dto;

import java.time.LocalDate;

/**
 * 延误桌上的一行：一张逾期工单 + 它指定的纸和版眼下的情况。
 * 不落库——每次拉桌都按当天日期、纸/版的眼下状态现算，
 * 所以改交期、补纸、修版之后，天数和加急标记自然跟着走，不会被钉死。
 */
public class DelayDeskRow {

    public Long id;
    public String jobNo;
    public String clientName;

    /** 待印 / 印刷中（已完成的单子不进桌）。 */
    public String jobState;

    public Long paperId;
    public String paperCode;
    public String paperName;
    /** 充足 / 紧张 / 缺货。 */
    public String paperState;
    /** 延误是不是纸缺货引起的。 */
    public boolean paperBlocked;

    public Long plateId;
    public String plateCode;
    public String plateName;
    /** 在用 / 已磨损 / 已作废。 */
    public String plateState;
    /** 延误是不是印版已磨损引起的。 */
    public boolean plateBlocked;

    public Integer copies;
    public LocalDate dueDate;

    /** 今天减交期：交期当天为 0，过了交期当天起算（桌上只放 > 0 的）。 */
    public int overdueDays;

    /** 加急：逾期超过 URGENT_OVERDUE_DAYS 天，或纸缺货，或版已磨损。 */
    public boolean urgent;

    /** 给调度看的一句话：逾期天数 + 是纸拖的还是版拖的。 */
    public String reason;
}
