package com.furocash.service;

import com.furocash.dao.UserDao;
import com.furocash.dto.ProfileUpdateRequest;
import com.furocash.dto.UserResponse;
import com.furocash.exception.FuroCashException;
import com.furocash.exception.InvalidInputException;
import com.furocash.exception.UserNotFoundException;
import com.furocash.util.DBConnection;
import com.furocash.util.InputValidator;
import java.sql.Connection;
import java.sql.SQLException;

public final class ProfileService {

  private final UserDao users = new UserDao();

  public UserResponse get(long userId) {
    return UserResponse.from(new AuthService().currentUser(userId));
  }

  public UserResponse update(long userId, ProfileUpdateRequest request) {
    if (request == null) throw new InvalidInputException(
      "Profile details are required"
    );
    String name = request.fullName() == null ? null : request.fullName().trim();
    if (
      !InputValidator.isValidFullName(name) ||
      name.codePoints().anyMatch(Character::isISOControl)
    ) {
      throw new InvalidInputException(
        "Full name is required (max 100 characters)"
      );
    }
    String email =
      request.email() == null || request.email().isBlank()
        ? null
        : request.email().trim();
    if (
      email != null &&
      (email.length() > 254 ||
        email.codePoints().anyMatch(Character::isISOControl) ||
        !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
    ) {
      throw new InvalidInputException(
        "Enter a valid email address (max 254 characters)"
      );
    }
    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);
      try {
        users
          .findByIdForUpdate(conn, userId)
          .orElseThrow(() -> new UserNotFoundException("User not found"));
        users.updateProfile(conn, userId, name, email);
        UserResponse result = UserResponse.from(
          users.findById(conn, userId).orElseThrow()
        );
        conn.commit();
        return result;
      } catch (SQLException | RuntimeException e) {
        conn.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new FuroCashException("Database error", e);
    }
  }
}
