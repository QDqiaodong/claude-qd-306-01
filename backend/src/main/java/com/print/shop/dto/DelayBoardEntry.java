package com.print.shop.dto;

import java.time.LocalDate;

/**
 * 延误桌上的一行。这不是台账，不落库——每次看桌都按眼下的
 * 工单交期、纸张状态、印版状态重新算，所以改交期、补纸、修版
 * 之后桌上跟着变，不会有钉死的加急。
 */
public class DelayBoardEntry {

    public Long jobId;
    public String jobNo;
    public String clientName;
    /** 只会有：待印 / 印刷中（已完成的不上桌）。 */
    public String jobState;
    public Integer copies;

    public LocalDate dueDate;
    /** 已经超过交期几天（至少 1 天才会上桌）。 */
    public long overdueDays;

    /** 加急：超期超过三天，或者纸/版在拖（哪怕刚超一天）。 */
    public boolean urgent;

    /** 谁拖的：纸 / 版 / 纸+版；纯超期没这些拖累时是 null。 */
    public String dragBy;

    /** 指定纸张眼下缺货。 */
    public boolean paperOut;
    /** 指定印版眼下已磨损。 */
    public boolean plateWorn;

    // —— 桌上直接要读的列，省得页面再回查 ——

    public Long paperId;
    public String paperName;
    public String paperState;

    public Long plateId;
    public String plateCode;
    public String plateState;
}
