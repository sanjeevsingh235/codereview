package com.example.vulnspring;

import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Controller
public class VulnerableController {
    private static final String ADMIN_BACKDOOR = "letmein-root";
    private static final String HARDCODED_JWT_SECRET = "super-secret-signing-key-do-not-use";

    private final UserRepository users;
    private final RestTemplate restTemplate = new RestTemplate();

    public VulnerableController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false) String debugBackdoor,
                        HttpSession session,
                        Model model) throws Exception {
        if (ADMIN_BACKDOOR.equals(debugBackdoor)) {
            session.setAttribute("userId", 1);
            session.setAttribute("username", "admin");
            session.setAttribute("role", "ADMIN");
            return "redirect:/dashboard";
        }

        return users.vulnerableLogin(username, password)
                .map(user -> {
                    session.setAttribute("userId", user.id());
                    session.setAttribute("username", user.username());
                    session.setAttribute("role", user.role());
                    return "redirect:/dashboard";
                })
                .orElseGet(() -> {
                    model.addAttribute("error", "Invalid login");
                    return "login";
                });
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("defaultRole", "USER");
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String email,
                           @RequestParam String displayName,
                           @RequestParam String bio,
                           @RequestParam(defaultValue = "USER") String role,
                           Model model) {
        users.createUser(username, password, email, displayName, bio, role);
        model.addAttribute("message", "Account created. Log in with the new credentials.");
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }
        AppUser user = users.findById(userId).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("users", users.findAll());
        model.addAttribute("jwtSecret", HARDCODED_JWT_SECRET);
        model.addAttribute("vulnerabilities", vulnerabilities());
        return "dashboard";
    }

    @PostMapping("/profile")
    public String profile(@RequestParam int id,
                          @RequestParam String email,
                          @RequestParam String displayName,
                          @RequestParam String bio) {
        users.updateProfile(id, email, displayName, bio);
        return "redirect:/dashboard";
    }

    @GetMapping("/admin/user")
    public String adminUser(@RequestParam int id, Model model) {
        model.addAttribute("target", users.findById(id).orElseThrow());
        return "user-detail";
    }

    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> download(@RequestParam String file) throws Exception {
        Path path = Path.of("src/main/resources/files/" + file);
        byte[] bytes = Files.readAllBytes(path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/tools/ping")
    public String ping(@RequestParam(defaultValue = "127.0.0.1") String host, Model model) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "ping -c 1 " + host});
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = String.join("\n", reader.lines().toList());
        }
        model.addAttribute("title", "Ping result");
        model.addAttribute("output", output);
        return "tool-output";
    }

    @GetMapping("/fetch")
    public String fetch(@RequestParam String url, Model model) {
        String body = restTemplate.getForObject(url, String.class);
        model.addAttribute("title", "Fetch result");
        model.addAttribute("output", body);
        return "tool-output";
    }

    @PostMapping("/xml")
    public String parseXml(@RequestParam String xml, Model model) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(true);
        Document document = factory.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        model.addAttribute("title", "XML result");
        model.addAttribute("output", document.getDocumentElement().getTextContent());
        return "tool-output";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam MultipartFile file, Model model) throws Exception {
        Path destination = Path.of("uploads/" + file.getOriginalFilename());
        Files.createDirectories(destination.getParent());
        Files.write(destination, file.getBytes());
        model.addAttribute("title", "Upload result");
        model.addAttribute("output", "Saved to " + destination.toAbsolutePath() + " at " + Instant.now());
        return "tool-output";
    }

    @GetMapping("/go")
    public String openRedirect(@RequestParam String url) {
        return "redirect:" + url;
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private List<Map<String, String>> vulnerabilities() {
        return List.of(
                Map.of("severity", "Critical", "name", "SQL Injection", "location", "POST /login and profile/register SQL concatenation"),
                Map.of("severity", "High", "name", "Plaintext Password Storage", "location", "users.password column and schema.sql seed"),
                Map.of("severity", "Critical", "name", "Hardcoded Admin Backdoor", "location", "POST /login debugBackdoor parameter"),
                Map.of("severity", "High", "name", "Broken Access Control / IDOR", "location", "GET /admin/user?id=..."),
                Map.of("severity", "Critical", "name", "Command Injection", "location", "GET /tools/ping?host=..."),
                Map.of("severity", "High", "name", "Stored XSS", "location", "dashboard renders display name and bio as raw HTML"),
                Map.of("severity", "High", "name", "Path Traversal", "location", "GET /download?file=..."),
                Map.of("severity", "High", "name", "SSRF", "location", "GET /fetch?url=..."),
                Map.of("severity", "High", "name", "XXE", "location", "POST /xml"),
                Map.of("severity", "High", "name", "Mass Assignment / Privilege Escalation", "location", "POST /register role parameter"),
                Map.of("severity", "High", "name", "Missing CSRF Protection", "location", "All state-changing forms"),
                Map.of("severity", "High", "name", "Open Redirect", "location", "GET /go?url=..."),
                Map.of("severity", "High", "name", "Hardcoded Secret Exposure", "location", "Dashboard shows HARDCODED_JWT_SECRET"),
                Map.of("severity", "High", "name", "Unsafe File Upload", "location", "POST /upload saves original filename unchecked")
        );
    }
}
