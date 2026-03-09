package com.dsi.rfp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfpEntities {

    // General
    private String clientName;
    private String submissionDeadline;
    private String issueDate;
    private String methodOfSelection;
    private String procurementMethod;
    private String projectDuration;
    private String preBidMeeting;
    private String contact;

    // Submission
    private String guidelinesSummary;
    private String numberOfCopies;
    private String softSubmissionRequired;
    private String submissionAddress;

    // Financial
    private String technicalFinancialSplit;
    private String performanceSecurity;
    private String bankGuarantee;
    private String paymentTerms;
    private String reimbursableExpenses;
    private String bidValidityPeriod;

    // ICT
    private String totalUsers;
    private String concurrentUsers;
    private String programmingLanguagePreference;
    private String systemLanguage;
    private String architecture;
    private String techStack;
    private String database;
    private String hosting;
    private Boolean dataMigrationRequired;
    private String legacySystem;
    private String hardwareRequirements;
    private String integrations;
    private Boolean mobileAppRequired;
    private Boolean uiMockRequired;
    private Boolean presentationRequired;
    private Boolean ganttChartRequired;
    private String eGovernanceCompliance;

    // Staffing
    private String staffMonths;
    private String onsiteResourceRequirements;
    private String markingCriteria;
    private String keyPersonnel;
    private String cvRequirements;

    // Support
    private String training;
    private String supportMaintenance;
    private String warrantyPeriod;

    // Evaluation
    private Object criteria;
    private String criteriaTotalWeight;
    private String eligibilitySummary;
    private String scopeSummary;
    private String scopeOfWork;
    private String similarProjectExperience;
}
