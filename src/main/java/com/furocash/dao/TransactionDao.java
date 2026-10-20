package com.furocash.dao;

import com.furocash.model.Transaction;
import com.furocash.model.TransactionType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TransactionDao {

    private Transaction mapRow(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setId(rs.getLong("id"));
        t.setStatus(rs.getString("status"));
        t.setCardId(rs.getString("card_id"));
        t.setUserId(rs.getLong("user_id"));
        t.setType(TransactionType.valueOf(rs.getString("type")));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setDetails(rs.getString("details"));
        t.setReferenceNo(rs.getString("reference_no"));
        t.setBalanceAfter(rs.getBigDecimal("balance_after"));

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        if (createdAtTs != null) {
            t.setCreatedAt(createdAtTs.toLocalDateTime());
        }
        return t;
    }

    public long insert(Connection conn, Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions (user_id, type, amount, details, reference_no, balance_after) VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, t.getUserId());
            ps.setString(2, t.getType().name());
            ps.setBigDecimal(3, t.getAmount());
            ps.setString(4, t.getDetails());
            ps.setString(5, t.getReferenceNo());
            ps.setBigDecimal(6, t.getBalanceAfter());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        throw new SQLException("Failed to insert transaction, no ID obtained.");
    }

    public List<Transaction> findByUserId(Connection conn, long userId) throws SQLException {
        String sql = "SELECT id, user_id, type, amount, details, reference_no, balance_after, created_at, status, card_id FROM transactions WHERE user_id = ? ORDER BY created_at DESC";
        List<Transaction> transactions = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapRow(rs));
                }
            }
        }
        return transactions;
    }

    public List<Transaction> findByUserId(Connection conn, long userId, int limit, int offset) throws SQLException {
        String sql = "SELECT id, user_id, type, amount, details, reference_no, balance_after, created_at, status, card_id "
                + "FROM transactions WHERE user_id = ? "
                + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }
}