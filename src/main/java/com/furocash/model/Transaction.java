package com.furocash.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
//encupsolation
public class Transaction {
    private long id;
    private long userId;
    private TransactionType type;
    private BigDecimal amount;
    private String details;
    private String referenceNo;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;

//empty constructors
    public Transaction() {
    }
//constructors
    public Transaction(long id, long userId, TransactionType type, BigDecimal amount, String details, String referenceNo, BigDecimal balanceAfter, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.details = details;
        this.referenceNo = referenceNo;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

//getter and setter
    public long getId(){return id;}
    public void setId(long id){this.id =id;}

    public long getUserId(){return userId;}
    public void setUserId(long userId){this.userId = userId;}

    public TransactionType getType(){return type;}
    public void setType(TransactionType type){this.type = type;}

    public BigDecimal getAmount(){return amount;}
    public void setAmount(BigDecimal amount){this.amount = amount;}

    public String getDetails(){return details;}
    public void setDetails(String details){this.details = details;}

    public String getReferenceNo(){return referenceNo;}
    public void setReferenceNo(String referenceNo){this.referenceNo = referenceNo;}

    public BigDecimal getBalanceAfter(){return balanceAfter;}
    public void setBalanceAfter(BigDecimal balanceAfter){this.balanceAfter = balanceAfter;}

    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setCreatedAt(LocalDateTime createdAt){this.createdAt = createdAt;}

}

