package com.furocash.dao;


import com.furocash.model.OtpRecord;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.Optional;
import java.sql.Timestamp;

public class OtpDao {
    public Optional<OtpRecord> findByMobile(Connection conn, String mobile) throws SQLException {
        String sql = "SELECT mobile_number, code_hash, expires_at, attempts, last_sent_at FROM otp_codes WHERE mobile_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mobile);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new OtpRecord(
                            rs.getString("mobile_number"),
                            rs.getString("code_hash"),
                            rs.getTimestamp("expires_at").toLocalDateTime(),
                            rs.getInt("attempts"),
                            rs.getTimestamp("last_sent_at").toLocalDateTime()));
                }
            }
        }
        return Optional.empty();
    }

public void upsert(Connection conn, String mobile, String codeHash, LocalDateTime expiresAt, LocalDateTime sentAt) throws SQLException {
        String sql = "INSERT INTO otp_codes (mobile_number, code_hash, expires_at, attempts, last_sent_at) " +
                     "VALUES(?, ?, ?, 0, ?) " +
                     "ON CONFLICT (mobile_number) DO UPDATE SET code_hash = EXCLUDED.code_hash," +
                     "expires_at = EXCLUDED.expires_at, attempts = 0, last_sent_at = EXCLUDED.last_sent_at ";
        try (PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, mobile);
            ps.setString(2, codeHash);
            ps.setTimestamp(3, Timestamp.valueOf(expiresAt));
            ps.setTimestamp(4, Timestamp.valueOf(sentAt));
            ps.executeUpdate();

        }

}

public void incrementAttempts(Connection conn, String mobile) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE otp_codes SET attempts = attempts + 1 WHERE mobile_number = ?")) {
            ps.setString(1, mobile);
            ps.executeUpdate();
        }


}

public void delete(Connection conn, String mobile) throws  SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM otp_codes WHERE mobile_number = ? ")){
            ps.setString(1, mobile);
            ps.executeUpdate();
        }
}



}
