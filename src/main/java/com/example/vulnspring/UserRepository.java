package com.example.vulnspring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    private final RowMapper<AppUser> mapper = (rs, rowNum) -> mapUser(rs);

    public UserRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public Optional<AppUser> vulnerableLogin(String username, String password) throws Exception {
        String sql = "SELECT * FROM users WHERE username = '" + username + "' AND password = '" + password + "'";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return Optional.of(mapUser(rs));
            }
            return Optional.empty();
        }
    }

    public void createUser(String username, String password, String email, String displayName, String bio, String role) {
        String sql = "INSERT INTO users (username, password, email, display_name, bio, role, api_key) VALUES ('"
                + username + "', '" + password + "', '" + email + "', '" + displayName + "', '" + bio + "', '"
                + role + "', 'USER-KEY-" + username + "')";
        jdbcTemplate.execute(sql);
    }

    public Optional<AppUser> findById(int id) {
        List<AppUser> users = jdbcTemplate.query("SELECT * FROM users WHERE id = " + id, mapper);
        return users.stream().findFirst();
    }

    public List<AppUser> findAll() {
        return jdbcTemplate.query("SELECT * FROM users ORDER BY id", mapper);
    }

    public void updateProfile(int id, String email, String displayName, String bio) {
        String sql = "UPDATE users SET email='" + email + "', display_name='" + displayName + "', bio='" + bio + "' WHERE id=" + id;
        jdbcTemplate.execute(sql);
    }

    private AppUser mapUser(ResultSet rs) throws java.sql.SQLException {
        return new AppUser(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("bio"),
                rs.getString("role"),
                rs.getString("api_key")
        );
    }
}
