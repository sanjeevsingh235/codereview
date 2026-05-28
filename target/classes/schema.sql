DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(80) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    display_name VARCHAR(255),
    bio CLOB,
    role VARCHAR(40) DEFAULT 'USER',
    api_key VARCHAR(255)
);

INSERT INTO users (username, password, email, display_name, bio, role, api_key)
VALUES ('admin', 'admin123', 'admin@example.test', 'Administrator', 'Built-in admin user', 'ADMIN', 'HARDCODED-ADMIN-KEY-12345');
