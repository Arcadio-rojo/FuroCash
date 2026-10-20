package com.furocash.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class User {

  private long id;
  private int sessionVersion;

  public int getSessionVersion() {
    return sessionVersion;
  }

  public void setSessionVersion(int version) {
    sessionVersion = version;
  }

  private String mobileNumber;
  private String pinHash;
  private String fullName;
  private String email;
  private LocalDateTime createdAt;
  private BigDecimal balance;
  private List<Transaction> transactions = new ArrayList<>();
  private int failedAttempts;
  private LocalDateTime lockedUntil;

  public User() {}

  public User(
    long id,
    String mobileNumber,
    String pinHash,
    String fullName,
    BigDecimal balance,
    List<Transaction> transactions,
    int failedAttempts,
    LocalDateTime lockedUntil
  ) {
    this.id = id;
    this.mobileNumber = mobileNumber;
    this.pinHash = pinHash;
    this.fullName = fullName;
    this.balance = balance;
    this.transactions = transactions;
    this.failedAttempts = failedAttempts;
    this.lockedUntil = lockedUntil;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getMobileNumber() {
    return mobileNumber;
  }

  public void setMobileNumber(String mobileNumber) {
    this.mobileNumber = mobileNumber;
  }

  public String getPinHash() {
    return pinHash;
  }

  public void setPinHash(String pinHash) {
    this.pinHash = pinHash;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }

  public List<Transaction> getTransactions() {
    return transactions;
  }

  public void setTransactions(List<Transaction> transactions) {
    this.transactions = transactions;
  }

  public int getFailedAttempts() {
    return failedAttempts;
  }

  public void setFailedAttempts(int failedAttempts) {
    this.failedAttempts = failedAttempts;
  }

  public LocalDateTime getLockedUntil() {
    return lockedUntil;
  }

  public void setLockedUntil(LocalDateTime lockedUntil) {
    this.lockedUntil = lockedUntil;
  }
}
