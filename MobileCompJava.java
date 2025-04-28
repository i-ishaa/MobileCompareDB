//Application.java:------------------------------------------------------------------------------
package com.example.phone_comparison_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}



//DataLoader.java:--------------------------------------------------------------------------------------------------
package com.example.phone_comparison_backend;

import com.example.phone_comparison_backend.service.PhoneService;
import com.example.phone_comparison_backend.repository.PhoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private PhoneService phoneService;

    @Autowired
    private PhoneRepository phoneRepository;

    @Override
    public void run(String... args) throws Exception {
        // Check if the database is empty before loading data from CSV
        if (phoneRepository.count() == 0) {
            phoneService.loadPhonesFromCsv();
            System.out.println("Data loaded successfully from CSV to database.");
        } else {
            System.out.println("Data already exists in the database. Skipping CSV loading.");
        }

        // Initialize the Trie with data from the database
        phoneService.initializeSpellCheck();
    }
}



//Controller:

//PhoneController.java--------------------------------------------------------------------------

package com.example.phone_comparison_backend.controller;

import com.example.phone_comparison_backend.repository.PhoneRepository;
import com.example.phone_comparison_backend.service.FrequencyCountService;
import com.example.phone_comparison_backend.service.InvertedIndexService;
import com.example.phone_comparison_backend.service.PhoneService;
import com.example.phone_comparison_backend.service.SpellCheckService;
import com.example.phone_comparison_backend.service.PhoneSorterService;
import com.example.phone_comparison_backend.util.WordCompletion;
import com.example.phone_comparison_backend.model.Phone;
import com.example.phone_comparison_backend.model.SearchTerm;
import com.example.phone_comparison_backend.model.SortRequest;
import com.example.phone_comparison_backend.model.PhoneComparison;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")  // Apply CORS to all methods in this controller
@RestController
@RequestMapping("/phones")
public class PhoneController {

    @Autowired
    private PhoneService phoneService;

    @Autowired
    private FrequencyCountService frequencyCountService;

    @Autowired
    private SpellCheckService spellCheckService;

    @Autowired
    private PhoneSorterService phoneSorterService;

    @Autowired
    private InvertedIndexService invertedIndexService;


    private final PhoneRepository phoneRepository;

    @Autowired
    private WordCompletion wordCompletionService; // WordCompletion service for word suggestions

    @Autowired
    public PhoneController(PhoneRepository phoneRepository) {
        this.phoneRepository = phoneRepository;
    }

    @GetMapping("/compare/detailed")
    public ResponseEntity<PhoneComparison> comparePhonesDetailed(@RequestParam Long id1, @RequestParam Long id2) {
        try {
            PhoneComparison comparison = phoneService.comparePhonesDetailed(id1, id2);
            return ResponseEntity.ok(comparison);
        } catch (RuntimeException e) {
            System.err.println("Runtime exception while comparing phones: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (Exception e) {
            System.err.println("Error comparing phones: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Enable CORS for all origins (can be restricted later if needed)
    // @CrossOrigin(origins = "*")

    // Endpoint to get all phones
    @GetMapping
    public List<Phone> getAllPhones() {
        return phoneService.getAllPhones();
    }

    @GetMapping("/compare")
public ResponseEntity<Map<String, Object>> comparePhones(@RequestParam Long phone1, @RequestParam Long phone2) {
    try {
        Map<String, Object> comparisonData = phoneService.comparePhones(phone1, phone2);
        if (comparisonData == null || comparisonData.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);  // Return 404 if no comparison data
        }
        return ResponseEntity.ok(comparisonData);  // Return 200 with comparison data
    } catch (Exception e) {
        // Log the error to check what went wrong
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An error occurred while fetching comparison data."));
    }
}



    // Endpoint to search phones by model or company
    @GetMapping("/search")
    public List<Phone> searchPhones(@RequestParam(required = false) String model,
                                    @RequestParam(required = false) String company) {
        return phoneService.searchPhones(model, company);
    }

    // Endpoint to get search term statistics
    @GetMapping("/search-stats")
    public List<SearchTerm> getSearchStatistics() {
        return phoneService.getSearchStatistics();
    }

    // Endpoint to get frequency of a search term
    @PostMapping("/searchFrequency")
    public int getSearchTermFrequency(@RequestBody String searchTerm) {
        return frequencyCountService.getSearchTermFrequency(searchTerm);
    }

    // Endpoint to check the spelling of a search term and get suggestions
    @GetMapping("/spellcheck")
    public List<String> checkSpelling(@RequestParam String searchTerm) {
        // Validate the input
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return List.of("Invalid input: Search term cannot be null or empty.");
        }

        // Convert the search term to lowercase for uniformity
        String normalizedSearchTerm = searchTerm.toLowerCase();

        // Check if the word exists in the Trie
        if (spellCheckService.checkIfWordExists(normalizedSearchTerm)) {
            return List.of("Word exists in the vocabulary.");
        } else {
            // If the word doesn't exist, return suggestions
            return spellCheckService.suggestWords(normalizedSearchTerm);
        }
    }

    // New endpoint to sort phones by price or model
    @PostMapping("/sort")
    public List<Phone> sortPhones(@RequestBody SortRequest sortRequest) {
        List<Phone> phones = phoneService.getAllPhones(); // Get all phones

        // Check if the list of phones is empty
        if (phones.isEmpty()) {
            return List.of(); // Return empty list if no phones are available
        }

        // Check the sorting criteria and apply the sorting
        if ("price".equalsIgnoreCase(sortRequest.getSortBy())) {
            return phoneSorterService.sortByPrice(phones, sortRequest.isAscending());
        } else if ("model".equalsIgnoreCase(sortRequest.getSortBy())) {
            return phoneSorterService.sortByModel(phones, sortRequest.isAscending());
        }

        // Handle invalid sort criteria
        throw new IllegalArgumentException("Invalid sort criteria. Please use 'price' or 'model'.");
    }

    @GetMapping("/database-word-count")
    public int getDatabaseWordCount(@RequestParam String term) {
        return phoneService.getDatabaseWordCount(term);
    }

    // New endpoint for word completion
    @GetMapping("/word-completion")
    public ResponseEntity<List<String>> completeWord(@RequestParam String prefix) {
        System.out.println("Received prefix: " + prefix);  // Debugging line

        // Validate input
        if (prefix == null || prefix.trim().isEmpty()) {
            System.out.println("Invalid input: Prefix cannot be null or empty.");
            return new ResponseEntity<>(List.of("Invalid input: Prefix cannot be null or empty."), HttpStatus.BAD_REQUEST);
        }

        // Fetch phone models based on the prefix
        List<String> models = phoneService.getPhoneModelsByPrefix(prefix);
        System.out.println("Phone models fetched: " + models);  // Debugging line

        // Insert the fetched phone models into the WordCompletion service (AVL tree)
        for (String model : models) {
            wordCompletionService.insert(model);  // Insert each model into the AVL tree
        }

        // Use the WordCompletion service to find suggestions based on the prefix
        List<String> suggestions = wordCompletionService.findSuggestions(prefix);
        System.out.println("Suggestions found: " + suggestions);  // Debugging line

        // If no suggestions were found, return a message
        if (suggestions.isEmpty()) {
            return new ResponseEntity<>(List.of("No suggestions found."), HttpStatus.OK);
        }

        // Return the list of suggestions
        return new ResponseEntity<>(suggestions, HttpStatus.OK);
    }

    // Endpoint to get phone models by prefix and insert them into WordCompletion
    @GetMapping("/models")
    public List<String> getPhoneModelsByPrefix(@RequestParam String prefix) {
        System.out.println("Received prefix: " + prefix); // Debugging line
        List<String> models = phoneService.getPhoneModelsByPrefix(prefix);
        System.out.println("Returning models: " + models); // Debugging line
        return models;
    }

    @GetMapping("/search-word")
    public ResponseEntity<Object> searchWord(@RequestParam String word) {
        System.out.println("Received word to search: " + word); // Debug statement to log the received word

        // Check if the search word is empty
        if (word == null || word.trim().isEmpty()) {
            System.out.println("Search word is empty. Returning no results.");
            return ResponseEntity.ok(new HashMap<>());  // Return an empty JSON object to maintain consistency
        }

        // Call the service to search for the word in files
        Map<String, Map<Integer, String>> searchResults = invertedIndexService.searchWord(word);

        if (searchResults.isEmpty()) {
            System.out.println("No results found for the word: " + word);
            return ResponseEntity.ok(new HashMap<>());  // Return an empty JSON object
        } else {
            System.out.println("Returning search results for the word: " + word);
            return ResponseEntity.ok(searchResults);  // Return the actual search results as a JSON response
        }
    }
}




//RegistrationController.java--------------------------------------------------------------------
package com.example.phone_comparison_backend.controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;


@RestController
@RequestMapping("/auth")
public class RegistrationController {

    private static final String CSV_FILE1 = "src/main/resources/users.csv";

    // Regular expressions for validation
    private static final String EMAIL_REGEX = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$";
    private static final String PASSWORD_REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$";
    private static final String USERNAME_REGEX = "^[a-zA-Z0-9_]{3,15}$"; // 3-15 characters
    private static final String PHONE_REGEX = "^\\+1\\s?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}$";
    static {
        try {
            if (!Files.exists(Paths.get(CSV_FILE1))) {
                try (FileWriter writer = new FileWriter(CSV_FILE1, true)) {
                    writer.append("username,email,password,phone\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestParam String username, 
                                               @RequestParam String email, 
                                               @RequestParam String password,
                                               @RequestParam String phone) {
        // Error message initialization
        StringBuilder errorMessage = new StringBuilder();

        // Regex checks for input fields
        if (!Pattern.matches(USERNAME_REGEX, username)) {
            errorMessage.append("Invalid username. It must be 3-15 characters long and can only contain letters, numbers, and underscores.\n");
        }
        if (!Pattern.matches(EMAIL_REGEX, email)) {
            errorMessage.append("Invalid email format. Please provide a valid email address.\n");
        }
        if (!Pattern.matches(PASSWORD_REGEX, password)) {
            errorMessage.append("Invalid password. It must contain at least one lowercase letter, one uppercase letter, one number, and be at least 8 characters long.\n");
        }
        if (!Pattern.matches(PHONE_REGEX, phone)) {
            errorMessage.append("Invalid phone number. It must be 10-15 digits long and should start with '+' (For Canada and US).\n");
        }

        // Check if username, email, or phone already exists
        if (isUsernameTaken(username)) {
            errorMessage.append("Username is already taken. Please choose another one.\n");
        }
        if (isEmailTaken(email)) {
            errorMessage.append("Email is already registered. Please login.\n");
        }
        if (isPhoneTaken(phone)) {
            errorMessage.append("Phone number is already registered. Please use a different one.\n");
        }

        // If any errors are found, return them
        if (errorMessage.length() > 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage.toString());
        }

        // If no errors, write the new user details to the CSV file
        try (FileWriter writer = new FileWriter(CSV_FILE1, true)) {
            writer.append(username).append(",").append(email).append(",").append(password).append(",").append(phone).append("\n");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        // Successfully registered, redirect to login page
        return ResponseEntity.status(HttpStatus.FOUND)
                             .header("Location", "/login.html")
                             .build();
    }

    // Helper method to check if username is already taken
    private boolean isUsernameTaken(String username) {
        return checkFieldExists(username, 0);
    }

    // Helper method to check if email is already taken
    private boolean isEmailTaken(String email) {
        return checkFieldExists(email, 1);
    }

    // Helper method to check if phone is already taken
    private boolean isPhoneTaken(String phone) {
        return checkFieldExists(phone, 3);
    }

    private boolean checkFieldExists(String value, int index) {
        try (BufferedReader reader = new BufferedReader(new FileReader(CSV_FILE1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length > index && fields[index].equals(value)) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    @PostMapping("/login")
    public ResponseEntity<String> loginUser(@RequestParam String email, @RequestParam String password) {
        // Validate email and password format using regex
        if (!Pattern.matches(EMAIL_REGEX, email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email format.");
        }
        if (!Pattern.matches(PASSWORD_REGEX, password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid password format.");
        }
    
        try {
            // Check credentials against CSV file
            List<String> lines = Files.readAllLines(Paths.get(CSV_FILE1));
            for (String line : lines) {
                String[] userData = line.split(",");
                if (userData.length >= 3 && userData[1].equals(email) && userData[2].equals(password)) {
                    // Redirect to homepage after successful login
                    return ResponseEntity.status(HttpStatus.FOUND)
                                         .header("Location", "/index.html")
                                         .build();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error.");
        }
    
        // Return UNAUTHORIZED if credentials are incorrect
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid login credentials.");
    }
}




//Model:

//Phone.java:------------------------------------------------------------------------------------
package com.example.phone_comparison_backend.model;

import jakarta.persistence.*;


@Entity
public class Phone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String model;
    @Column(name = "image_url", length = 1000)
    private String imageUrl;
    private Float price;
    private String company;
    private String productLink;

    // New fields
    private String os;
    private String ram;
    private String rom;
    private String is5G;
    private String isDualSim;
    private String bluetoothVersion;
    private String hasFastCharging;

    public Phone() {
    }

    public Phone(String model, String imageUrl, Float price, String company, String productLink) {
        this.model = model;
        this.imageUrl = imageUrl;
        this.price = price;
        this.company = company;
        this.productLink = productLink;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Float getPrice() {
        return price;
    }

    public void setPrice(Float price) {
        this.price = price;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getProductLink() {
        return productLink;
    }

    public void setProductLink(String productLink) {
        this.productLink = productLink;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getRam() {
        return ram;
    }

    public void setRam(String ram) {
        this.ram = ram;
    }

    public String getRom() {
        return rom;
    }

    public void setRom(String rom) {
        this.rom = rom;
    }

    public String getIs5G() {
        return is5G;
    }

    public void set5G(String is5G) {
        this.is5G = is5G;
    }

    public String getIsDualSim() {
        return isDualSim;
    }

    public void setDualSim(String isDualSim) {
        this.isDualSim = isDualSim;
    }

    public String getBluetoothVersion() {
        return bluetoothVersion;
    }

    public void setBluetoothVersion(String bluetoothVersion) {
        this.bluetoothVersion = bluetoothVersion;
    }

    public String getHasFastCharging() {
        return hasFastCharging;
    }

    public void setHasFastCharging(String hasFastCharging) {
        this.hasFastCharging = hasFastCharging;
    }

    @Override
    public String toString() {
        return "Phone{" +
                "id=" + id +
                ", model='" + model + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", price=" + price +
                ", company='" + company + '\'' +
                ", productLink='" + productLink + '\'' +
                ", os='" + os + '\'' +
                ", ram='" + ram + '\'' +
                ", rom='" + rom + '\'' +
                ", is5G='" + is5G + '\'' +
                ", isDualSim='" + isDualSim + '\'' +
                ", bluetoothVersion='" + bluetoothVersion + '\'' +
                ", hasFastCharging='" + hasFastCharging + '\'' +
                '}';
    }
}



//PhoneComparison.java:---------------------------------------------------------------------------
package com.example.phone_comparison_backend.model;

public class PhoneComparison {
    private Phone phone1;
    private Phone phone2;
    private String comparisonResult;

    public PhoneComparison(Phone phone1, Phone phone2, String comparisonResult) {
        this.phone1 = phone1;
        this.phone2 = phone2;
        this.comparisonResult = comparisonResult;
    }

    // Getters and Setters
    public Phone getPhone1() {
        return phone1;
    }

    public void setPhone1(Phone phone1) {
        this.phone1 = phone1;
    }

    public Phone getPhone2() {
        return phone2;
    }

    public void setPhone2(Phone phone2) {
        this.phone2 = phone2;
    }

    public String getComparisonResult() {
        return comparisonResult;
    }

    public void setComparisonResult(String comparisonResult) {
        this.comparisonResult = comparisonResult;
    }
}


//RankablePhone.java:-------------------------------------------------------------------------------

package com.example.phone_comparison_backend.model;

public class RankablePhone implements Comparable<RankablePhone> {
    private Phone phone;
    private int frequency;

    public RankablePhone(Phone phone, int frequency) {
        this.phone = phone;
        this.frequency = frequency;
    }

    public Phone getPhone() {
        return phone;
    }

    public int getFrequency() {
        return frequency;
    }

    @Override
    public int compareTo(RankablePhone other) {
        // Max-heap based on frequency
        return Integer.compare(other.frequency, this.frequency);
    }
}


//SearchTerm.java:--------------------------------------------------------------------------------
package com.example.phone_comparison_backend.model;

import jakarta.persistence.*;

@Entity
public class SearchTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String term;

    private int frequency;

    public SearchTerm() {}

    public SearchTerm(String term, int frequency) {
        this.term = term;
        this.frequency = frequency;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }
}



//SortRequest.java:--------------------------------------------------------------------------------
package com.example.phone_comparison_backend.model;

public class SortRequest {

    private String sortBy;  // price or model
    private boolean ascending;  // true for ascending, false for descending

    // Getters and setters
    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public boolean isAscending() {
        return ascending;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }
}


//Repository:

//PhoneRepository:------------------------------------------------------------------------------
package com.example.phone_comparison_backend.repository;

import com.example.phone_comparison_backend.model.Phone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PhoneRepository extends JpaRepository<Phone, Long> {

    // Existing methods
    List<Phone> findByCompany(String company); // Add query method for company
    List<Phone> findByModelContaining(String model); // Example search by model
    List<Phone> findByModelContainingAndCompany(String model, String company); // Search by model and company
    int countByModelContainingOrCompanyContaining(String model, String company);

    @Query("SELECT p.model FROM Phone p WHERE LOWER(p.model) LIKE CONCAT(LOWER(:prefix), '%')")
    List<String> findPhoneModelsByPrefix(@Param("prefix") String prefix);

}



//SearchTermRepository:---------------------------------------------------------------------------------------------------
package com.example.phone_comparison_backend.repository;

import com.example.phone_comparison_backend.model.SearchTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SearchTermRepository extends JpaRepository<SearchTerm, Long> {
    Optional<SearchTerm> findByTerm(String term);
}



//Service:

//DocumentFileReaderService:------------------------------------------------------------------------------------------------

package com.example.phone_comparison_backend.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class DocumentFileReaderService {

    public String readFileContent(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        System.out.println("File read successful");
        return new String(Files.readAllBytes(path));  // Read all content of the file
    }
}


//FrequencyCountService:---------------------------------------------------------------------------------------------------

package com.example.phone_comparison_backend.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class FrequencyCountService {

    // In-memory map to store search term frequencies
    private Map<String, Integer> searchTermFrequencyMap = new HashMap<>();

    // Boyer-Moore algorithm for pattern matching
    public int boyerMooreCount(String text, String pattern) {
        int n = text.length();
        int m = pattern.length();
        int[] badCharacterShift = new int[256]; // ASCII size

        // Initialize all occurrences as -1
        for (int i = 0; i < 256; i++) {
            badCharacterShift[i] = -1;
        }

        // Build the bad character shift table
        for (int i = 0; i < m; i++) {
            badCharacterShift[pattern.charAt(i)] = i;
        }

        int count = 0;
        int shift = 0;
        while (shift <= n - m) {
            int j = m - 1;
            while (j >= 0 && pattern.charAt(j) == text.charAt(shift + j)) {
                j--;
            }

            if (j < 0) {
                count++;
                // Shift the pattern based on bad character heuristic
                shift += (shift + m < n) ? m - badCharacterShift[text.charAt(shift + m)] : 1;
            } else {
                shift += Math.max(1, j - badCharacterShift[text.charAt(shift + j)]);
            }
        }
        return count;
    }

    // Function to get the frequency of a search term
    public int getSearchTermFrequency(String searchTerm) {
        // Get the current frequency from the in-memory map
        searchTermFrequencyMap.putIfAbsent(searchTerm, 0);  // Initialize if not present
        int frequency = searchTermFrequencyMap.get(searchTerm);
        // Increment the frequency for each search term
        searchTermFrequencyMap.put(searchTerm, frequency + 1);
        return frequency + 1;  // Return the updated frequency
    }

    // Optional: method to reset the frequency map (could be useful in future scenarios)
    public void resetSearchTermFrequencies() {
        searchTermFrequencyMap.clear();  // This will reset all the frequencies when needed
    }
}

//InvertedIndexService.java:-----------------------------------------------------------------------------------------------------
package com.example.phone_comparison_backend.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Service
public class InvertedIndexService {

    // Node for Ternary Search Tree
    private static class TSTNode {
        char character;
        boolean isEndOfWord;
        TSTNode left, middle, right;

        public TSTNode(char character) {
            this.character = character;
        }
    }

    private TSTNode root;

    // Method to insert a word into the TST
    public void insert(String word) {
        if (word == null || word.trim().isEmpty()) {
            return; // Ignore empty or null words
        }
        root = insert(root, word.toCharArray(), 0);
    }

    private TSTNode insert(TSTNode node, char[] word, int index) {
        char currentChar = word[index];
        if (node == null) {
            node = new TSTNode(currentChar);
        }

        if (currentChar < node.character) {
            node.left = insert(node.left, word, index);
        } else if (currentChar > node.character) {
            node.right = insert(node.right, word, index);
        } else {
            if (index + 1 < word.length) {
                node.middle = insert(node.middle, word, index + 1);
            } else {
                node.isEndOfWord = true; // Mark the end of the word
            }
        }
        return node;
    }

    // Method to search for a word in the files
    public Map<String, Map<Integer, String>> searchWord(String word) {
        Map<String, Map<Integer, String>> results = new HashMap<>();

        if (word == null || word.trim().isEmpty()) {
            System.out.println("Empty search term provided.");
            return results; // Return empty result if no word is provided
        }

        String directoryPath = "TextFiles";

        File directory = new File(directoryPath);
        File[] txtFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));

        if (txtFiles == null || txtFiles.length == 0) {
            System.out.println("No text files found in directory.");
            return results; // Return empty result if no files are found
        }

        System.out.println("Searching for word: " + word);

        for (File file : txtFiles) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
                if (content.isEmpty()) {
                    System.out.println("Skipping empty file: " + file.getAbsolutePath());
                    continue; // Skip empty files
                }

                // Build the TST from file content
                buildTST(content);

                // Search for occurrences of the word in the file
                Map<Integer, String> occurrences = findWordOccurrences(content, word);

                if (!occurrences.isEmpty()) {
                    System.out.println("Found occurrences in file: " + file.getAbsolutePath());
                    results.put(file.getAbsolutePath(), occurrences);
                }
            } catch (IOException e) {
                System.err.println("Error reading file " + file.getAbsolutePath() + ": " + e.getMessage());
            }
        }

        if (results.isEmpty()) {
            System.out.println("No occurrences found for word: " + word);
        } else {
            System.out.println("Found occurrences in " + results.size() + " files.");
        }

        return results; // Return the map with the search results
    }

    // Method to build a TST from content
    private void buildTST(String content) {
        root = null; // Reset the TST for each file's content
        String[] words = content.split("\\W+"); // Split by non-word characters

        for (String word : words) {
            insert(word); // Insert each word into the TST
        }
    }

    // Method to find occurrences of a word in the content using TST
    private Map<Integer, String> findWordOccurrences(String content, String word) {
        Map<Integer, String> occurrences = new HashMap<>();

        int index = content.indexOf(word); // Start searching from the first occurrence

        while (index != -1) {
            occurrences.put(index, word); // Store the occurrence index
            index = content.indexOf(word, index + 1); // Search for the next occurrence
        }

        return occurrences;
    }
}



//PhoneService.java:---------------------------------------------------------------------------------------------------

package com.example.phone_comparison_backend.service;
import com.example.phone_comparison_backend.model.Phone;
import com.example.phone_comparison_backend.model.PhoneComparison;
import com.example.phone_comparison_backend.model.SearchTerm;
import com.example.phone_comparison_backend.repository.PhoneRepository;
import com.example.phone_comparison_backend.repository.SearchTermRepository;
import com.example.phone_comparison_backend.util.KMPAlgorithm;
import com.example.phone_comparison_backend.util.WordCompletion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.opencsv.CSVReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;



@Service
public class PhoneService {

    @Autowired
    private PhoneRepository phoneRepository;

    @Autowired
    private SearchTermRepository searchTermRepository;

    @Autowired
    private SpellCheckService spellCheckService;

    @Autowired
    private WordCompletion wordCompletion;

    @Autowired
    private DocumentFileReaderService documentFileReaderService;

    public void loadPhonesFromCsv() throws Exception {
        List<Phone> phoneList = new ArrayList<>();
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(
                new ClassPathResource("phones.csv").getInputStream()))) {
            String[] line;
            csvReader.readNext(); // Skip header
            while ((line = csvReader.readNext()) != null) {
                String model = line[0];
                String imageUrl = line[1];
                Float price = parsePrice(line[2]);
                String company = line[3];
                String productLink = line.length > 4 ? line[4] : "";

                // New fields
                String os = line.length > 5 ? line[5] : "";
                String ram = line.length > 6 ? line[6] : "";
                String rom = line.length > 7 ? line[7] : "";
                String is5G = line.length > 8 ? line[8].trim().equalsIgnoreCase("Supported") ? "Yes" : "No" : "No";
            String isDualSim = line.length > 9 ? line[9].trim().equalsIgnoreCase("Supported") ? "Yes" : "No" : "No";
            String hasFastCharging = line.length > 11 ? line[11].trim().equalsIgnoreCase("Supported") ? "Yes" : "No" : "No";

            // Bluetooth version and other fields
            String bluetoothVersion = line.length > 10 ? line[10].trim().replace(",", ";") : "";

                Phone phone = new Phone(model, imageUrl, price, company, productLink);
                phone.setOs(os);
                phone.setRam(ram);
                phone.setRom(rom);
                phone.set5G(is5G);
                phone.setDualSim(isDualSim);
                phone.setBluetoothVersion(bluetoothVersion);
                phone.setHasFastCharging(hasFastCharging);

                phoneList.add(phone);
            }
        }
        phoneRepository.saveAll(phoneList);
        initializeSpellCheck();
    }

    public List<Phone> findPhonesByIds(List<Long> phoneIds) {
        return phoneRepository.findAllById(phoneIds);
    }

    public PhoneComparison comparePhonesDetailed(Long id1, Long id2) {
        Phone phone1 = phoneRepository.findById(id1)
                .orElseThrow(() -> new RuntimeException("Phone with id " + id1 + " not found"));
        Phone phone2 = phoneRepository.findById(id2)
                .orElseThrow(() -> new RuntimeException("Phone with id " + id2 + " not found"));

        StringBuilder comparisonBuilder = new StringBuilder();

        // Compare OS
        if (!phone1.getOs().equals(phone2.getOs())) {
            comparisonBuilder.append("Different OS: ").append(phone1.getOs()).append(" vs ").append(phone2.getOs()).append(".\n");
        } else {
            comparisonBuilder.append("Same OS: ").append(phone1.getOs()).append(".\n");
        }

        // Compare RAM
        if (!phone1.getRam().equals(phone2.getRam())) {
            comparisonBuilder.append("Different RAM: ").append(phone1.getRam()).append(" vs ").append(phone2.getRam()).append(".\n");
        } else {
            comparisonBuilder.append("Same RAM: ").append(phone1.getRam()).append(".\n");
        }

        // Compare ROM
        if (!phone1.getRom().equals(phone2.getRom())) {
            comparisonBuilder.append("Different ROM: ").append(phone1.getRom()).append(" vs ").append(phone2.getRom()).append(".\n");
        } else {
            comparisonBuilder.append("Same ROM: ").append(phone1.getRom()).append(".\n");
        }

        // Compare 5G capability
        if (!phone1.getIs5G().equals(phone2.getIs5G())) {
            comparisonBuilder.append("5G support differs.\n");
        } else {
            comparisonBuilder.append("Both support 5G.\n");
        }

        // Compare Dual SIM capability
        if (!phone1.getIsDualSim().equals(phone2.getIsDualSim())) {
            comparisonBuilder.append("Dual SIM support differs.\n");
        } else {
            comparisonBuilder.append("Both support Dual SIM.\n");
        }

        // Compare Bluetooth version
        if (!phone1.getBluetoothVersion().equals(phone2.getBluetoothVersion())) {
            comparisonBuilder.append("Different Bluetooth Version: ").append(phone1.getBluetoothVersion())
                    .append(" vs ").append(phone2.getBluetoothVersion()).append(".\n");
        } else {
            comparisonBuilder.append("Same Bluetooth Version: ").append(phone1.getBluetoothVersion()).append(".\n");
        }

        // Compare Fast Charging capability
        if (!phone1.getHasFastCharging().equals(phone2.getHasFastCharging())) {
            comparisonBuilder.append("Fast Charging support differs.\n");
        } else {
            comparisonBuilder.append("Both support Fast Charging.\n");
        }

        return new PhoneComparison(phone1, phone2, comparisonBuilder.toString());
    }

    public Map<String, Object> comparePhones(Long phone1Id, Long phone2Id) {
    Map<String, Object> comparisonData = new HashMap<>();

    Phone phone1 = phoneRepository.findById(phone1Id).orElse(null);
    Phone phone2 = phoneRepository.findById(phone2Id).orElse(null);

    if (phone1 != null && phone2 != null) {
        comparisonData.put("Model", Map.of("phone1", phone1.getModel(), "phone2", phone2.getModel()));
        comparisonData.put("Price", Map.of("phone1", phone1.getPrice(), "phone2", phone2.getPrice()));
        comparisonData.put("Company", Map.of("phone1", phone1.getCompany(), "phone2", phone2.getCompany()));
        comparisonData.put("OS", Map.of("phone1", phone1.getOs(), "phone2", phone2.getOs()));
        comparisonData.put("RAM", Map.of("phone1", phone1.getRam(), "phone2", phone2.getRam()));
        comparisonData.put("ROM", Map.of("phone1", phone1.getRom(), "phone2", phone2.getRom()));
        comparisonData.put("5G Support", Map.of("phone1", phone1.getIs5G(), "phone2", phone2.getIs5G()));
        comparisonData.put("Dual SIM Support", Map.of("phone1", phone1.getIsDualSim(), "phone2", phone2.getIsDualSim()));
        comparisonData.put("Bluetooth Version", Map.of("phone1", phone1.getBluetoothVersion(), "phone2", phone2.getBluetoothVersion()));
        comparisonData.put("Fast Charging Support", Map.of("phone1", phone1.getHasFastCharging(), "phone2", phone2.getHasFastCharging()));
        // Add more features as necessary
    }

    return comparisonData;
}


    private Float parsePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isEmpty()) return 0.0f;
        String sanitizedPrice = rawPrice.replaceAll("[^\\d.]", "");
        if (sanitizedPrice.indexOf('.') != sanitizedPrice.lastIndexOf('.')) {
            System.err.println("Invalid price format with multiple decimal points: " + rawPrice);
            return 0.0f;
        }
        try {
            return sanitizedPrice.isEmpty() ? 0.0f : Float.parseFloat(sanitizedPrice);
        } catch (NumberFormatException e) {
            System.err.println("Invalid price format: " + rawPrice);
            return 0.0f;
        }
    }

    public List<Phone> getAllPhones() {
        return phoneRepository.findAll();
    }

    public List<Phone> searchPhones(String model, String company) {
        if (model != null && !model.isEmpty()) {
            trackSearchTerm(model);
        }
        if (company != null && !company.isEmpty()) {
            trackSearchTerm(company);
        }
        List<Phone> phones = new ArrayList<>();
        if (model != null && company != null) {
            phones = phoneRepository.findByModelContainingAndCompany(model, company);
        } else if (model != null) {
            phones = phoneRepository.findByModelContaining(model);
        } else if (company != null) {
            phones = phoneRepository.findByCompany(company);
        } else {
            phones = phoneRepository.findAll();
        }
        return phones;
    }

    public List<Phone> sortPhonesByPrice(List<Phone> phones, boolean ascending) {
        quickSort(phones, 0, phones.size() - 1, ascending, "price");
        return phones;
    }

    public List<Phone> sortPhonesByModel(List<Phone> phones, boolean ascending) {
        quickSort(phones, 0, phones.size() - 1, ascending, "model");
        return phones;
    }

    private void quickSort(List<Phone> phones, int low, int high, boolean ascending, String sortBy) {
        if (low < high) {
            int pivotIndex = partition(phones, low, high, ascending, sortBy);
            quickSort(phones, low, pivotIndex - 1, ascending, sortBy);
            quickSort(phones, pivotIndex + 1, high, ascending, sortBy);
        }
    }

    private int partition(List<Phone> phones, int low, int high, boolean ascending, String sortBy) {
        Phone pivot = phones.get(high);
        int i = low - 1;
        for (int j = low; j < high; j++) {
            boolean condition = false;
            if ("price".equalsIgnoreCase(sortBy)) {
                condition = ascending ? phones.get(j).getPrice() < pivot.getPrice() : phones.get(j).getPrice() > pivot.getPrice();
            }
            if ("model".equalsIgnoreCase(sortBy)) {
                condition = ascending ? phones.get(j).getModel().compareTo(pivot.getModel()) < 0 : phones.get(j).getModel().compareTo(pivot.getModel()) > 0;
            }
            if (condition) {
                i++;
                Phone temp = phones.get(i);
                phones.set(i, phones.get(j));
                phones.set(j, temp);
            }
        }
        Phone temp = phones.get(i + 1);
        phones.set(i + 1, phones.get(high));
        phones.set(high, temp);
        return i + 1;
    }

    private void trackSearchTerm(String term) {
        Optional<SearchTerm> existingTerm = searchTermRepository.findByTerm(term);
        if (existingTerm.isPresent()) {
            SearchTerm searchTerm = existingTerm.get();
            searchTerm.setFrequency(searchTerm.getFrequency() + 1);
            searchTermRepository.save(searchTerm);
        } else {
            searchTermRepository.save(new SearchTerm(term, 1));
        }
    }

    public List<SearchTerm> getSearchStatistics() {
        return searchTermRepository.findAll();
    }

    public void initializeSpellCheck() {
        spellCheckService.loadVocabulary();
    }

    public List<String> getWordCompletions(String prefix) {
        return wordCompletion.findSuggestions(prefix.toLowerCase());
    }

    public int getDatabaseWordCount(String searchTerm) {
        List<Phone> allPhones = phoneRepository.findAll();
        int totalCount = 0;
        for (Phone phone : allPhones) {
            totalCount += KMPAlgorithm.countOccurrences(phone.getModel().toLowerCase(), searchTerm.toLowerCase());
            totalCount += KMPAlgorithm.countOccurrences(phone.getCompany().toLowerCase(), searchTerm.toLowerCase());
        }
        return totalCount;
    }

    public List<String> getPhoneModelsByPrefix(String prefix) {
        System.out.println("Received request to fetch phone models with prefix: " + prefix);
        if (prefix == null || prefix.trim().isEmpty()) {
            System.out.println("Prefix is null or empty. Returning empty list.");
            return List.of();
        }
        List<String> models = phoneRepository.findPhoneModelsByPrefix(prefix);
        System.out.println("Fetched phone models: " + models);
        if (models.isEmpty()) {
            System.out.println("No phone models found with the prefix: " + prefix);
        }
        for (String model : models) {
            System.out.println("Inserting model into AVL tree: " + model);
            wordCompletion.insert(model);
        }
        return models;
    }

    public List<Phone> searchAndRankPhones(String searchTerm) {
        List<Phone> allPhones = phoneRepository.findAll();
        Map<Phone, Integer> frequencyMap = new HashMap<>();

        // Count occurrences of the search term in each phone's model and company
        for (Phone phone : allPhones) {
            int count = countOccurrences(phone.getModel(), searchTerm) + countOccurrences(phone.getCompany(), searchTerm);
            frequencyMap.put(phone, count);
        }

        // Sort phones based on frequency in descending order
        return frequencyMap.entrySet().stream()
                .filter(entry -> entry.getValue() > 0) // Only include phones with non-zero occurrences
                .sorted(Map.Entry.<Phone, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int countOccurrences(String text, String searchTerm) {
        if (text == null || searchTerm == null || searchTerm.isEmpty()) {
            return 0;
        }
        return (text.toLowerCase().length() - text.toLowerCase().replace(searchTerm.toLowerCase(), "").length()) / searchTerm.length();
 
    }
public void demoSearchInFile() throws IOException {
        String searchWord = "phone";  // Word to search for
        String filePath = "C:\\Users\\Vidhi\\Documents\\abcd.txt";  // File to search in

        System.out.println("[DEBUG] Starting demo search with word: " + searchWord + " in file: " + filePath);

        List<String> results = searchWordInFile(searchWord, filePath);

        // Debug: Output the results
        if (results.isEmpty()) {
            System.out.println("[DEBUG] No occurrences found for the word: " + searchWord);
        } else {
            System.out.println("[DEBUG] Occurrences of the word \"" + searchWord + "\":");
            for (String result : results) {
                System.out.println("[DEBUG] " + result);
            }
        }
    }

    public List<String> searchWordInFile(String searchWord, String filePath) throws IOException {
        List<String> results = new ArrayList<>();

        // Debug: Check if search word is empty
        if (searchWord == null || searchWord.trim().isEmpty()) {
            System.out.println("[DEBUG] Search word is null or empty. No search will be performed.");
            return results;  // If the search word is empty, return no results
        }

        // Ensure searchWord is case-insensitive and trimmed
        searchWord = searchWord.trim().toLowerCase();
        System.out.println("[DEBUG] Search word after trimming and converting to lowercase: " + searchWord);

        // Read the content of the file
        System.out.println("[DEBUG] Reading file from path: " + filePath);
        String content = documentFileReaderService.readFileContent(filePath).toLowerCase(); // Make content lowercase

        // Debug: Print the content being read from the file
        System.out.println("[DEBUG] File content read from file: " + content.substring(0, Math.min(content.length(), 100)) + "...");
        // Print the first 100 characters to avoid overwhelming the logs

        // Use word boundaries to ensure whole word matches
        String regex = "\\b" + Pattern.quote(searchWord) + "\\b";  // Ensure the word is matched as a whole (not part of another word)
        System.out.println("[DEBUG] Regex pattern used for matching: " + regex);

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);

        // Find all occurrences of the searchWord in the content using regex
        int count = 0;
        while (matcher.find()) {
            results.add("Found at index: " + matcher.start());
            count++;
            // Debug: Log each match's index
            System.out.println("[DEBUG] Found match at index: " + matcher.start());
        }

        // Debug: Print results
        if (count == 0) {
            System.out.println("[DEBUG] No matches found for the word: " + searchWord);
        } else {
            System.out.println("[DEBUG] Total matches found: " + count);
        }

        return results;
    }

    
}




//PhoneSorterService.java:-------------------------------------------------------------------------------------------------
package com.example.phone_comparison_backend.service;

import com.example.phone_comparison_backend.model.Phone;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PhoneSorterService {

    // Quick Sort for price
    public List<Phone> sortByPrice(List<Phone> phones, boolean ascending) {
        quickSort(phones, 0, phones.size() - 1, ascending, "price");
        return phones;
    }

    // Quick Sort for model name
    public List<Phone> sortByModel(List<Phone> phones, boolean ascending) {
        quickSort(phones, 0, phones.size() - 1, ascending, "model");
        return phones;
    }

    // Quick Sort implementation
    private void quickSort(List<Phone> phones, int low, int high, boolean ascending, String sortBy) {
        if (low < high) {
            int pivotIndex = partition(phones, low, high, ascending, sortBy);
            quickSort(phones, low, pivotIndex - 1, ascending, sortBy);  // Left part
            quickSort(phones, pivotIndex + 1, high, ascending, sortBy); // Right part
        }
    }

    // Partition function for Quick Sort
    private int partition(List<Phone> phones, int low, int high, boolean ascending, String sortBy) {
        Phone pivot = phones.get(high); // Last element as pivot
        int i = low - 1;

        for (int j = low; j < high; j++) {
            boolean condition = false;

            // Sort by price
            if ("price".equalsIgnoreCase(sortBy)) {
                condition = ascending ? phones.get(j).getPrice() < pivot.getPrice() : phones.get(j).getPrice() > pivot.getPrice();
            }

            // Sort by model
            if ("model".equalsIgnoreCase(sortBy)) {
                condition = ascending ? phones.get(j).getModel().compareTo(pivot.getModel()) < 0 : phones.get(j).getModel().compareTo(pivot.getModel()) > 0;
            }

            // Swap if condition is met
            if (condition) {
                i++;
                swap(phones, i, j);
            }
        }

        // Swap pivot element to correct position
        swap(phones, i + 1, high);

        return i + 1;
    }

    // Utility method to swap two elements in the list
    private void swap(List<Phone> phones, int i, int j) {
        Phone temp = phones.get(i);
        phones.set(i, phones.get(j));
        phones.set(j, temp);
    }
}



//SearchTermService.java:-----------------------------------------------------------------------------------------------
package com.example.phone_comparison_backend.service;

import com.example.phone_comparison_backend.model.SearchTerm;
import com.example.phone_comparison_backend.repository.SearchTermRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SearchTermService {

    @Autowired
    private SearchTermRepository searchTermRepository;

    public void recordSearchTerm(String term) {
        if (term == null || term.trim().isEmpty()) {
            return;
        }

        term = term.toLowerCase(); // Normalize term for case-insensitive tracking

        // Check if term already exists
        Optional<SearchTerm> existingTerm = searchTermRepository.findByTerm(term);
        if (existingTerm.isPresent()) {
            SearchTerm searchTerm = existingTerm.get();
            searchTerm.setFrequency(searchTerm.getFrequency() + 1);
            searchTermRepository.save(searchTerm);
        } else {
            // Add new search term with frequency 1
            searchTermRepository.save(new SearchTerm(term, 1));
        }
    }

    public Iterable<SearchTerm> getAllSearchTerms() {
        return searchTermRepository.findAll();
    }
}



//SpellCheckService.java:----------------------------------------------------------------------------------------------------
package com.example.phone_comparison_backend.service;

import com.example.phone_comparison_backend.model.Phone;
import com.example.phone_comparison_backend.repository.PhoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class SpellCheckService {

    @Autowired
    private PhoneRepository phoneRepository;

    private TrieNode root;

    public SpellCheckService() {
        this.root = new TrieNode();
    }

    // Load the vocabulary into the Trie from the database
    public void loadVocabulary() {
        List<Phone> phones = phoneRepository.findAll();
        for (Phone phone : phones) {
            insertWord(phone.getModel().toLowerCase());
            insertWord(phone.getCompany().toLowerCase()); // Load brand names too
        }
        // Print all words loaded into the Trie
        System.out.println("Words loaded into the Trie:");
        List<String> allWords = getAllWordsFromTrie();
        for (String word : allWords) {
            System.out.println(word);
        }
    }

    // Insert a word into the Trie
    private void insertWord(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            if (!node.containsKey(c)) {
                node.put(c, new TrieNode());
            }
            node = node.get(c);
        }
        node.setEndOfWord(true);
    }

    // Method to check if a word exists in the Trie
    public boolean checkIfWordExists(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            if (!node.containsKey(c)) {
                return false; // If a character is missing, the word doesn't exist
            }
            node = node.get(c);
        }
        return node.isEndOfWord(); // Return true only if it's the end of a valid word
    }

    // Method to retrieve all words in the Trie
    public List<String> getAllWordsFromTrie() {
        List<String> result = new ArrayList<>();
        collectWords(root, new StringBuilder(), result);
        return result;
    }

    private void collectWords(TrieNode node, StringBuilder prefix, List<String> result) {
        if (node.isEndOfWord()) {
            result.add(prefix.toString());
        }
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            prefix.append(entry.getKey());
            collectWords(entry.getValue(), prefix, result);
            prefix.deleteCharAt(prefix.length() - 1);
        }
    }

    // Suggest words and print edit distances for debugging
    public List<String> suggestWords(String word) {
        List<String> suggestions = new ArrayList<>();
        int minDistance = Integer.MAX_VALUE;
        String closestMatch = null;

        // List to hold phones related to the closest match
        List<Phone> matchingPhones = new ArrayList<>();

        List<Phone> phones = phoneRepository.findAll();
        System.out.println("Calculating edit distances for word: " + word);

        for (Phone phone : phones) {
            String phoneModel = phone.getModel().toLowerCase();
            String phoneCompany = phone.getCompany().toLowerCase();

            // Calculate edit distance for model and company
            int modelDistance = calculateEditDistance(word.toLowerCase(), phoneModel);
            int companyDistance = calculateEditDistance(word.toLowerCase(), phoneCompany);

            System.out.println("Edit distance between '" + word + "' and model '" + phoneModel + "': " + modelDistance);
            System.out.println("Edit distance between '" + word + "' and brand '" + phoneCompany + "': " + companyDistance);

            if (modelDistance < minDistance) {
                minDistance = modelDistance;
                closestMatch = phoneModel;
                matchingPhones.clear(); // Clear previously matched phones
                matchingPhones.add(phone); // Add the matched phone
            } else if (modelDistance == minDistance) {
                matchingPhones.add(phone); // Add this phone to the matching phones list
            }

            if (companyDistance < minDistance) {
                minDistance = companyDistance;
                closestMatch = phoneCompany;
                matchingPhones.clear(); // Clear previously matched phones
                matchingPhones.add(phone); // Add the matched phone
            } else if (companyDistance == minDistance) {
                matchingPhones.add(phone); // Add this phone to the matching phones list
            }
        }

        // If a closest match is found, return suggestions and matching phones
        if (matchingPhones.size() > 0) {
            for (Phone phone : matchingPhones) {
                suggestions.add(phone.getModel()); // Add matching phone models to the suggestions
            }
        }

        System.out.println("Closest match(es): ");
        for (Phone phone : matchingPhones) {
            System.out.println("Model: " + phone.getModel() + ", Brand: " + phone.getCompany());
        }

        return suggestions;
    }

    // Calculate the edit distance (Levenshtein distance)
    private int calculateEditDistance(String word1, String word2) {
        int[][] dp = new int[word1.length() + 1][word2.length() + 1];

        for (int i = 0; i <= word1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= word2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= word1.length(); i++) {
            for (int j = 1; j <= word2.length(); j++) {
                int cost = (word1.charAt(i - 1) == word2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }

        return dp[word1.length()][word2.length()];
    }

    // Trie Node class
    private static class TrieNode {
        private Map<Character, TrieNode> children;
        private boolean isEndOfWord;

        public TrieNode() {
            children = new HashMap<>();
            isEndOfWord = false;
        }

        public boolean containsKey(char c) {
            return children.containsKey(c);
        }

        public TrieNode get(char c) {
            return children.get(c);
        }

        public void put(char c, TrieNode node) {
            children.put(c, node);
        }

        public boolean isEndOfWord() {
            return isEndOfWord;
        }

        public void setEndOfWord(boolean endOfWord) {
            isEndOfWord = endOfWord;
        }
    }
}



//util:

//KMPAlgorithm:-------------------------------------------------------------------------------------------------------------
package com.example.phone_comparison_backend.util;

public class KMPAlgorithm {
    public static int[] computeLPS(String pattern) {
        int[] lps = new int[pattern.length()];
        int len = 0;
        int i = 1;
        
        while (i < pattern.length()) {
            if (pattern.charAt(i) == pattern.charAt(len)) {
                len++;
                lps[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = lps[len - 1];
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }
        return lps;
    }

    public static int countOccurrences(String text, String pattern) {
        int[] lps = computeLPS(pattern);
        int i = 0, j = 0, count = 0;
        
        while (i < text.length()) {
            if (pattern.charAt(j) == text.charAt(i)) {
                i++;
                j++;
            }
            if (j == pattern.length()) {
                count++;
                j = lps[j - 1];
            } else if (i < text.length() && pattern.charAt(j) != text.charAt(i)) {
                if (j != 0) {
                    j = lps[j - 1];
                } else {
                    i++;
                }
            }
        }
        return count;
    }
}



//WordCompletion.java:---------------------------------------------------------------------------------------------------------
package com.example.phone_comparison_backend.util;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

@Service
public class WordCompletion {

    private Node root;

    // Node structure for AVL tree
    private static class Node {
        String word;
        Node left, right;
        int height;

        Node(String word) {
            this.word = word;
            this.height = 1; // New nodes have height 1
        }
    }

    // Insert a word into the AVL Tree
    public void insert(String word) {
        root = insert(root, word.toLowerCase()); // Insert word in lowercase to handle case-insensitivity
        System.out.println("Inserted word: " + word.toLowerCase()); // Debugging
    }

    // Helper function to insert word into the AVL tree
    private Node insert(Node node, String word) {
        if (node == null) {
            return new Node(word);  // Create a new node for the word
        }

        // Insert the word in the correct place in the tree
        if (word.compareTo(node.word) < 0) {
            node.left = insert(node.left, word);  // Insert into left subtree
        } else if (word.compareTo(node.word) > 0) {
            node.right = insert(node.right, word);  // Insert into right subtree
        } else {
            // Word already exists in the tree, no need to insert again
            return node;
        }

        // Update the height of the current node
        node.height = 1 + Math.max(getHeight(node.left), getHeight(node.right));

        // Balance the node if needed
        int balance = getBalance(node);

        // Balance the tree and apply rotations if needed
        if (balance > 1 && word.compareTo(node.left.word) < 0) {
            return rightRotate(node);  // Right rotation if left subtree is taller
        }
        if (balance < -1 && word.compareTo(node.right.word) > 0) {
            return leftRotate(node);  // Left rotation if right subtree is taller
        }
        if (balance > 1 && word.compareTo(node.left.word) > 0) {
            node.left = leftRotate(node.left);
            return rightRotate(node);  // Left-right rotation
        }
        if (balance < -1 && word.compareTo(node.right.word) < 0) {
            node.right = rightRotate(node.right);
            return leftRotate(node);  // Right-left rotation
        }

        return node;  // Return the unchanged node if no rotation is needed
    }

    // Helper functions for balancing the AVL tree (left and right rotations)
    private Node leftRotate(Node x) {
        Node y = x.right;
        Node T2 = y.left;
        y.left = x;
        x.right = T2;
        x.height = Math.max(getHeight(x.left), getHeight(x.right)) + 1;
        y.height = Math.max(getHeight(y.left), getHeight(y.right)) + 1;
        return y;
    }

    private Node rightRotate(Node y) {
        Node x = y.left;
        Node T2 = x.right;
        x.right = y;
        y.left = T2;
        y.height = Math.max(getHeight(y.left), getHeight(y.right)) + 1;
        x.height = Math.max(getHeight(x.left), getHeight(x.right)) + 1;
        return x;
    }

    private int getHeight(Node node) {
        return node == null ? 0 : node.height;
    }

    private int getBalance(Node node) {
        return node == null ? 0 : getHeight(node.left) - getHeight(node.right);
    }

    // Find all words starting with the given prefix
    public List<String> findSuggestions(String prefix) {
        Set<String> suggestionsSet = new HashSet<>();  // Use a Set to ensure uniqueness
        findSuggestions(root, prefix.toLowerCase(), suggestionsSet);  // Pass prefix in lowercase for consistency
        System.out.println("Total unique suggestions found: " + suggestionsSet.size()); // Debugging
        return new ArrayList<>(suggestionsSet);  // Convert the Set back to a List
    }

    // Helper function to find suggestions starting with the given prefix
    private void findSuggestions(Node node, String prefix, Set<String> suggestions) {
        if (node == null) {
            return;  // Base case: return if node is null
        }

        // Debugging: print the current node being processed
        System.out.println("Processing node: " + node.word);

        // Only process nodes that may contain words starting with the prefix
        if (node.word.startsWith(prefix)) {
            suggestions.add(node.word);  // Add to suggestions if it matches the prefix
            System.out.println("Added suggestion: " + node.word);  // Debugging
        }

        // Search left subtree if the prefix is lexicographically less than the node word
        if (prefix.compareTo(node.word) < 0) {
            System.out.println("Going left from node: " + node.word);  // Debugging
            findSuggestions(node.left, prefix, suggestions);
        }

        // Search right subtree if the prefix is lexicographically greater than the node word
        if (prefix.compareTo(node.word) > 0) {
            System.out.println("Going right from node: " + node.word);  // Debugging
            findSuggestions(node.right, prefix, suggestions);
        }

        // Additionally, always explore both subtrees if the current node matches the prefix
        if (node.word.startsWith(prefix)) {
            if (node.left != null) {
                System.out.println("Exploring left subtree of " + node.word);  // Debugging
                findSuggestions(node.left, prefix, suggestions);
            }
            if (node.right != null) {
                System.out.println("Exploring right subtree of " + node.word);  // Debugging
                findSuggestions(node.right, prefix, suggestions);
            }
        }
    }
}




//resources->static:

//index.html:---------------------------------------------------------------------------------------------------------------
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Phone Comparison</title>
    <link rel="stylesheet" href="style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css">
</head>
<body>
<!-- Header Section -->
<header>
    <h1>Smart Suggest</h1>
</header>

<!-- Navigation Bar Section -->
<nav>
    <ul id="navbar">
        <li><a href="/"> Home </a></li>
        <li><a href="compare.html">Compare</a></li>
        <li><a href="invertedindex.html">InvertedIndex</a></li>
        <!-- Wrap Register and Login in a container to keep them together -->
        <ul class="auth-links">
            <li><a href="registration.html" class="btn">Register</a></li> <!-- Link to Register Page -->
            <li><a href="./login.html" class="btn">Login</a></li>
        </ul>
    </ul>
</nav>

<!-- Search Bar and Company Filter Section -->
<div class="filters">
    <!-- Search Bar -->
    <input type="text" id="searchInput" placeholder="Search for phones..." oninput="handleSearchAndCount(); displayDatabaseWordCount(); handleWordCompletion()">
    <div id="databaseWordCountDisplay"></div>

    <!-- Company Filter Dropdown -->
    <select id="companyFilter" onchange="filterPhones()">
        <option value="">Select Company</option>
        <!-- Populate dynamically with companies -->
    </select>

    <!-- Sort Filter Dropdown -->
    <select id="sortFilter" onchange="applySort()">
        <option value="" disabled selected>Sort by</option>
        <option value="priceLowHigh">Price (Low-High)</option>
        <option value="priceHighLow">Price (High-Low)</option>
        <option value="modelAZ">Models (A-Z)</option>
        <option value="modelZA">Models (Z-A)</option>
    </select>
    <button id="resetFiltersBtn" class="button" onclick="resetAllFilters()">Reset Filters</button>
</div>

 <div class="filters">
 <!-- Suggestions Section -->
 <div id="suggestionsContainer" style="display: none; border: 1px solid #ccc; max-height: 200px; overflow-y: auto;"></div>
</div>
<div class="filters">
    <!-- Frequency Display Section -->
        <div></div>
    <div id="frequencyDisplay" style="display: none;"></div>
    </div>
<!-- Phone List Section -->
<div class="phone-list-container">
    <h2>Available Phones</h2>
    <div id="phoneListContainer" class="phone-list"></div>
</div>

<!-- Popup for error message -->
<div id="errorPopup" class="popup-container" style="display: none;">
    <div class="popup-content">
        <span id="closePopup" class="popup-close">&times;</span>
        <p id="errorMessage">Please enter the correct word.</p>
    </div>
</div>

<!-- Footer Section -->
<footer>
    <div class="footer-content">
        <div class="special-features">
            <h3>What Makes Our Project Special</h3>
            <ul class="features-list">
                <li>Front End GUI</li>
                <li>Email validation and regular expressions</li>
                <li>URL validation using patterns</li>
                <li>Spell checking</li>
                <li>Word completion</li>
                <li>Frequency count</li>
                <li>Search Frequency</li>
            </ul>
        </div>
    </div>
</footer>

<script src="script.js"></script>
</body>
</html>


//compare.css:---------------------------------------------------------------------------------------------------------------
/* General Styles */
body {
    font-family: Arial, sans-serif;
    background-color: #f4f4f4;
    margin: 0;
    padding: 20px;
}

header {
    background-color: #1e3a8a;
    color: #ffffff;
    padding: 20px;
    border-radius: 10px;
    margin-bottom: 20px;
    font-size: 24px;
    text-shadow: 1px 1px 4px #000000;
    text-align: center;
}

/* Container for the comparison section */
.container {
    max-width: 1200px;
    margin: auto;
    padding: 20px;
    background: white;
    border-radius: 8px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

/* Title Styles */
/* h1 {
    text-align: center;
    color: #333;
} */

/* Dropdown Styles */
.select-container {
    display: flex;
    justify-content: space-between;
    margin-bottom: 20px;
}

select {
    width: 48%;
    padding: 10px;
    border-radius: 5px;
    border: 1px solid #ccc;
}

/* Button Styles */
button {
    padding: 10px 15px;
    background-color: #28a745; /* Green */
    color: white;
    border: none;
    border-radius: 5px;
    cursor: pointer;
}

button:hover {
    background-color: #218838; /* Darker green on hover */
}

/* Comparison Table Styles */
table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 20px;
}

th, td {
    padding: 12px;
    text-align: left;
}

th {
    background-color: #007bff; /* Blue */
    color: white;
}

tr:nth-child(even) {
    background-color: #f2f2f2; /* Light gray for even rows */
}

tr:hover {
    background-color: #e9ecef; /* Light gray on hover */
}

/* Image Styles */
img {
    max-width: 100px; /* Limit image size */
}

/* Result Container Styles */
.comparisonResultContainer {
    margin-top: 20px;
}

/* Comparison Table Styles */
#comparisonTable {
    width: 100%;
    border-collapse: collapse;
    margin: 20px 0;
    font-size: 16px;
    text-align: left;
    background-color: #f9f9f9;
}

#comparisonTable thead {
    background-color: #4CAF50;
    color: white;
}

#comparisonTable th, #comparisonTable td {
    padding: 12px 15px;
    border: 1px solid #ddd;
}

#comparisonTable tr:nth-child(even) {
    background-color: #f2f2f2;
}

#comparisonTable tr:hover {
    background-color: #ddd;
}

/* Navigation Bar Styling */
nav {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 20px;
    background-color: #ffffff;
    border-radius: 5px;
    margin-bottom: 20px;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.05);
}

/* Navbar List */
#navbar {
    display: flex;
    list-style: none;
    gap: 20px;
}

#navbar li {
    cursor: pointer;
    padding: 10px;
    color: #1e3a8a;
    font-size: 20px;
    font-weight: bold;
    transition: color 0.3s ease;
}

#navbar li:hover {
    color: #2563eb;
}

nav {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 20px;
    background-color: #ffffff;
    border-radius: 5px;
    margin-bottom: 20px;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.05);
}

/* Style for the main navbar items */
#navbar {
    display: flex;
    list-style: none;
    gap: 20px; /* Space out the items */
    flex-grow: 1; /* Allow the navbar to grow and take available space */
}

/* Align the Register and Login buttons to the right */
.auth-links {
    margin-left: auto; /* Push the Register and Login to the far right */
    list-style: none;
    display: flex;
    gap: 10px; /* Space between Register and Login */
}

/* Styling for the Register and Login buttons */
.auth-links li a {
    text-decoration: none;
    padding: 10px;
    color: #1e3a8a;
    font-weight: bold;
    transition: color 0.3s ease;
}

/* Hover effect for Register and Login buttons */
.auth-links li a:hover {
    color: #2563eb;
}

a{
    text-decoration: none;
    color: #1e3a8a;
}

a:hover{
    color: rgb(15, 138, 239);
}


//compare.html:---------------------------------------------------------------------------------------------------
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="compare.css"> <!-- Link to your CSS file -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css">
    <title>Phone Comparison</title>
</head>
<body>
    <header>
    <h1>Compare Phones</h1>
    </header>
    <!-- Navigation Bar Section -->
<nav>
    <ul id="navbar">
        <li><a href="index.html"> Home </a></li>
        <li><a href="compare.html">Compare</a></li>
        <li><a href="invertedindex.html"> Inverted Index</a></li>
        <!-- Wrap Register and Login in a container to keep them together -->
        <ul class="auth-links">
            <li><a href="registration.html" class="btn">Register</a></li> <!-- Link to Register Page -->
            <li><a href="./login.html" class="btn">Login</a></li>
        </ul>
    </ul>
</nav>

    <div class="select-container">
        <select id="phone1Select">
            <option value="">Select Phone 1</option>
        </select>
        <select id="phone2Select">
            <option value="">Select Phone 2</option>
        </select>
        <button id="compareButton">Compare</button>
    </div>

    <div class="comparisonResultContainer" id="comparisonResultContainer">
        <table id="comparisonTable">
            <thead>
                <tr>
                    <th>Feature</th>
                    <th>Phone 1</th>
                    <th>Phone 2</th>
                </tr>
            </thead>
            <tbody>
                <!-- Data rows will be dynamically inserted here -->
            </tbody>
        </table>
    </div>

<script src="compare.js"></script> <!-- Link to your JavaScript file -->
</body>
</html>



//compare.js:-----------------------------------------------------------------------------------------------------------------
document.getElementById('compareButton').addEventListener('click', function() {
    const phone1Id = document.getElementById('phone1Select').value;
    const phone2Id = document.getElementById('phone2Select').value;

    // Check if both phones are selected
    if (!phone1Id || !phone2Id) {
        alert('Please select two phones to compare.');
        return;
    }

    // Fetch comparison data from the backend
    fetch(`http://localhost:8080/phones/compare?phone1=${phone1Id}&phone2=${phone2Id}`)
        .then(response => {
            if (!response.ok) {
                throw new Error(`Error fetching comparison: ${response.statusText}`);
            }
            return response.json();  // Parse JSON only if response is OK
        })
        .then(data => {
            // Pass the data to display in a table
            displayComparison(data);
        })
        .catch(error => {
            console.error(error);
            alert('An error occurred while fetching comparison data.');
        });
});

document.addEventListener('DOMContentLoaded', () => {
    // Fetch data from the backend
    fetch('http://localhost:8080/phones')
        .then(response => {
            if (!response.ok) {
                throw new Error('API error: ' + response.statusText);
            }
            return response.json();
        })
        .then(data => {
            populateSelect('phone1Select', data);
            populateSelect('phone2Select', data);
        })
        .catch(error => {
            console.error('Error fetching phones:', error);
        });
});

// Function to populate the dropdown
function populateSelect(selectId, phones) {
    const selectElement = document.getElementById(selectId);

    // Check if select element exists
    if (!selectElement) {
        console.error(`Dropdown with ID '${selectId}' not found.`);
        return;
    }

    // Clear existing options
    selectElement.innerHTML = '<option value="">Select a phone</option>';

    // Populate dropdown with phone models
    phones.forEach(phone => {
        const option = document.createElement('option');
        option.value = phone.id; // Set phone ID as the value
        option.textContent = phone.model; // Use phone model as display text
        selectElement.appendChild(option);
    });
}




async function fetchPhones() {
    try {
        const response = await fetch('/phones'); // Adjust this endpoint based on your API
        const phones = await response.json();
        populateDropdowns(phones);
    } catch (error) {
        console.error('Error fetching phones:', error);
    }
}

function populateDropdowns(phones) {
    const phone1Select = document.getElementById('phone1Select');
    const phone2Select = document.getElementById('phone2Select');

    phones.forEach(phone => {
        const option1 = new Option(phone.model, phone.id);
        const option2 = new Option(phone.model, phone.id);
        phone1Select.add(option1);
        phone2Select.add(option2);
    });
}

async function comparePhones() {
    const phone1Id = document.getElementById('phone1Select').value;
    const phone2Id = document.getElementById('phone2Select').value;

    if (phone1Id === phone2Id) {
        alert('Please select two different phones to compare.');
        return;
    }

    try {
        const response = await fetch(`/phones/compare/detailed?id1=${phone1Id}&id2=${phone2Id}`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const comparedPhones = await response.json();
        displayComparison(comparedPhones);
    } catch (error) {
        console.error('Error comparing phones:', error);
        alert(`An error occurred while comparing phones: ${error.message}`);
    }
}

function displayComparison(comparisonData) {
    const tbody = document.querySelector('#comparisonTable tbody');
    tbody.innerHTML = '';  // Clear the previous comparison

    // Loop through the comparison data and create rows for the table
    for (const feature in comparisonData) {
        if (comparisonData.hasOwnProperty(feature)) {
            const row = document.createElement('tr');
            
            // Feature Column
            const featureCell = document.createElement('td');
            featureCell.textContent = feature;
            row.appendChild(featureCell);

            // Phone 1 Column
            const phone1Cell = document.createElement('td');
            phone1Cell.textContent = comparisonData[feature].phone1 || 'N/A';
            row.appendChild(phone1Cell);

            // Phone 2 Column
            const phone2Cell = document.createElement('td');
            phone2Cell.textContent = comparisonData[feature].phone2 || 'N/A';
            row.appendChild(phone2Cell);

            // Append row to table
            tbody.appendChild(row);
        }
    }
}



//invertedindex.java:----------------------------------------------------------------------------------------------------------
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Inverted Index</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 30px;
        }

        .container {
            width: 90%;
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
            border: 1px solid #ddd;
            border-radius: 8px;
            height: auto;
            overflow: auto;
        }

        h1 {
            text-align: center;
        }

        .form-group {
            margin-bottom: 20px;
        }

        .form-group label {
            font-size: 16px;
            display: block;
            margin-bottom: 8px;
        }

        .form-group input {
            width: 100%;
            padding: 8px;
            font-size: 16px;
            border: 1px solid #ddd;
            border-radius: 4px;
            box-sizing: border-box;
        }

        .form-group button {
            padding: 10px 15px;
            font-size: 16px;
            background-color: #4CAF50;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
        }

        .form-group button:hover {
            background-color: #45a049;
        }

        #results {
            margin-top: 30px;
            border: 1px solid #ddd;
            padding: 20px;
            border-radius: 8px;
            background-color: #f9f9f9;
            min-height: 50px;
            height: auto;
            overflow: auto;
            word-wrap: break-word;
        }

        .result-item {
            margin-bottom: 10px;
            padding: 10px;
            background-color: white;
            border-radius: 4px;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
        }

        .result-item span {
            font-weight: bold;
        }

        @media screen and (max-width: 768px) {
            .container {
                width: 95%;
                margin: 10px auto;
                padding: 10px;
            }

            body {
                margin: 10px;
            }
        }

    </style>
</head>
<body>

<div class="container">
    <h1>Inverted Index</h1>

    <!-- Search Word Form -->
    <div class="form-group">
        <label for="search-word">Enter a word to search:</label>
        <input type="text" id="search-word" placeholder="Enter word">
        <button onclick="searchWord()">Search</button>
    </div>

    <!-- Display Results -->
    <div id="results"></div>
</div>

<!-- Load Script at the End of the Body Tag -->
<script>
    let invertedIndex = {};

    function searchWord() {
        console.log("Hello");
        const word = document.getElementById('search-word').value.trim();
        console.log("Searching for word:", word);

        if (word === '') {
            alert('Please enter a word to search.');
            console.log("No word entered, exit search.");
            return;
        }

        fetch(`/phones/search-word?word=${word}`)
        .then(response => response.json())
        .then(data => {
            console.log(data);

            if (Object.keys(data).length === 0) {
                document.getElementById('results').innerHTML = '<p>No occurrences found for the word.</p>';
                console.log("No results found for the word.");
            } else {
                displayResults(data);
            }
        })
        .catch(error => {
            console.error('Error:', error);
        });
    }

    function displayResults(data) {
        const resultsDiv = document.getElementById('results');
        resultsDiv.innerHTML = '';

        if (Object.keys(data).length === 0) {
            resultsDiv.innerHTML = '<p>No occurrences found for the word.</p>';
            console.log("No results found for the word.");
            return;
        }

        for (const filePath in data) {
            const indices = data[filePath];
            const fileName = filePath.split('\\').pop().split('/').pop();

            const resultItem = document.createElement('div');
            resultItem.classList.add('result-item');

            if (Array.isArray(indices)) {
    resultItem.innerHTML = `<span>File:</span> ${fileName} | <span>Positions:</span> ${indices.join(', ')}`;
} else if (typeof indices === 'object') {
    resultItem.innerHTML = `<span>File:</span> ${fileName} | <span>Positions:</span> ${JSON.stringify(indices)}`;
} else {
    resultItem.innerHTML = `<span>File:</span> ${fileName} | <span>Positions:</span> Invalid data format`;
}


            resultsDiv.appendChild(resultItem);
        }
    }

    function buildInvertedIndex(files) {
        console.log("Building inverted index...");
        invertedIndex = {};

        files.forEach(file => {
            const content = file.content.toLowerCase().split(' ');
            content.forEach((word, index) => {
                if (!invertedIndex[word]) {
                    invertedIndex[word] = [];
                }
                invertedIndex[word].push({ fileName: file.name, position: index });
            });
        });

        console.log("Inverted index built:", invertedIndex);
    }
</script>

</body>
</html>



//invertedindex.js:------------------------------------------------------------------------------------------------------------
// Global object to hold the inverted index (if needed)
// Global object to hold the inverted index (if needed)
// Global object to hold the inverted index (if needed)
// Global object to hold the inverted index (if needed)
let invertedIndex = {};

function searchWord() {
    console.log("Hello");
    const word = document.getElementById('search-word').value.trim();
    console.log("Searching for word:", word);

    if (word === '') {
        alert('Please enter a word to search.');
        console.log("No word entered, exit search.");
        return;
    }

    fetch(`/phones/search-word?word=${word}`)
    .then(response => response.json())
        .then(data => {
            console.log("Search result data:", data);

            // Check if the response data is empty or doesn't contain any matches
            if (Object.keys(data).length === 0) {
                document.getElementById('results').innerHTML = '<p>No occurrences found for the word.</p>';
                console.log("No results found for the word.");
            } else {
                displayResults(data); // Display the results if any data is found
            }
        })
        .catch(error => {
            console.error('Error fetching word occurrences:', error);
            document.getElementById('results').innerHTML = 'Error fetching word occurrences.';
        });
}
function displayResults(data) {
    const resultsDiv = document.getElementById('results');
    resultsDiv.innerHTML = '';

    if (Object.keys(data).length === 0) {
        resultsDiv.innerHTML = <p>No occurrences found for the word.</p>;
        console.log("No results found for the word.");
        return;
    }

    for (const filePath in data) {
        const indices = data[filePath];
        const fileName = filePath.split('\\').pop().split('/').pop();

        const resultItem = document.createElement('div');
        resultItem.classList.add('result-item');

        if (Array.isArray(indices)) {
            resultItem.innerHTML = `<span>File:</span> ${fileName} | <span>Positions:</span> ${indices.join(', ')}`;
        } else if (typeof indices === 'object') {
            resultItem.innerHTML = `<span>File:</span> ${fileName} | <span>Positions:</span> ${JSON.stringify(indices)}`;
        } else {
            resultItem.innerHTML = `<span>File:</span> ${fileName} | <span>Positions:</span> Invalid data format`;
        }
        

        resultsDiv.appendChild(resultItem);
    }
}

function buildInvertedIndex(files) {
    console.log("Building inverted index...");
    invertedIndex = {};

    files.forEach(file => {
        const content = file.content.toLowerCase().split(' ');
        content.forEach((word, index) => {
            if (!invertedIndex[word]) {
                invertedIndex[word] = [];
            }
            invertedIndex[word].push({ fileName: file.name, position: index });
        });
    });

    console.log("Inverted index built:", invertedIndex);
        }




//login.html:-------------------------------------------------------------------------------------------------------------
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f3f4f6;
            color: #333;
            margin: 0;
            padding: 0;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
        }

        .container {
            background-color: white;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
            width: 100%;
            max-width: 400px;
        }

        h2 {
            text-align: center;
            color: #0066cc;
        }

        label {
            font-size: 16px;
            color: #555;
            margin-bottom: 8px;
            display: block;
        }

        input[type="email"], input[type="password"], input[type="text"] {
            width: 95%;
            padding: 10px;
            margin: 10px 0;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 16px;
        }
        

        button {
            width: 100%;
            padding: 12px;
            background-color: #0066cc;
            color: white;
            border: none;
            border-radius: 4px;
            font-size: 16px;
            cursor: pointer;
            transition: background-color 0.3s;
        }

        button:hover {
            background-color: #004d99;
        }

        .register-btn {
            background-color: #4CAF50;
            margin-top: 10px;
        }

        .register-btn:hover {
            background-color: #3e8e41;
        }

        p {
            text-align: center;
            font-size: 14px;
            color: #cc0000;
            margin-top: 15px;
        }

        .form-group {
            margin-bottom: 15px;
        }

    </style>
</head>
<body>

    <div class="container">
        <h2>Login Page</h2>
        <form id="loginForm" action="/auth/login" method="post">
            <div class="form-group">
                <label for="email">Email:</label>
                <input type="text" id="email" name="email" required>
            </div>

            <div class="form-group">
                <label for="password">Password:</label>
                <input type="password" id="password" name="password" required>
            </div>

            <button type="submit">Login</button>
            <button type="button" class="register-btn" id="registerButton">Register</button>
        </form>
        <p id="message"></p>
    </div>

    <script>
        const form = document.getElementById("loginForm");
        const registerButton = document.getElementById("registerButton");

        // Regex for validation
        const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
        const passwordRegex = /^[A-Za-z0-9@#$%^&+=]{6,20}$/;

        // Form submission handler
        form.addEventListener("submit", async (e) => {
            e.preventDefault();

            const email = document.getElementById("email").value;
            const password = document.getElementById("password").value;
            const message = document.getElementById("message");

            if (!emailRegex.test(email)) {
                message.textContent = "Please enter a valid email address.";
                return;
            }

            if (!passwordRegex.test(password)) {
                message.textContent = "Password must be 6-20 characters long and include only letters, numbers, or @#$%^&+=";
                return;
            }

            const formData = new FormData(form);
            const response = await fetch(form.action, {
                method: "POST",
                body: formData,
            });

            if (response.redirected) {
                window.location.href = response.url; // Redirect to the dashboard or home page
            } else {
                message.textContent = "Invalid login credentials. Please try again.";
            }
        });

        // Redirect to registration page
        registerButton.addEventListener("click", () => {
            window.location.href = "registration.html";
        });
    </script>
</body>
</html>


//registration.html:-----------------------------------------------------------------------------------------------------------
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Registration</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f3f4f6;
            color: #333;
            margin: 0;
            padding: 0;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
        }

        .container {
            background-color: white;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
            width: 100%;
            max-width: 400px;
        }

        h2 {
            text-align: center;
            color: #0066cc;
        }

        label {
            font-size: 16px;
            color: #555;
            margin-bottom: 8px;
            display: block;
        }

        input[type="email"],
        input[type="password"],
        input[type="text"] {
            width: 95%;
            padding: 10px;
            margin: 10px 0;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 16px;
        }

        button {
            width: 100%;
            padding: 12px;
            background-color: #0066cc;
            color: white;
            border: none;
            border-radius: 4px;
            font-size: 16px;
            cursor: pointer;
            transition: background-color 0.3s;
        }

        button:hover {
            background-color: #004d99;
        }

        p {
            text-align: center;
            font-size: 14px;
            color: #cc0000;
            margin-top: 15px;
        }

        .form-group {
            margin-bottom: 15px;
        }

        .error-message {
            color: red;
            font-size: 14px;
            margin-top: 5px;
            display: none;
            /* Initially hidden */
        }
    </style>
</head>

<body>

    <div class="container">
        <h2>Registration Page</h2>
        <form id="registrationForm" action="/auth/register" method="post">
            <div class="form-group">
                <label for="username">Username:</label>
                <input type="text" id="username" name="username" required>
                <p id="usernameError" class="error-message"></p>
            </div>
        
            <div class="form-group">
                <label for="email">Email:</label>
                <input type="text" id="email" name="email" required>
                <p id="emailError" class="error-message"></p>
            </div>
        
            <div class="form-group">
                <label for="password">Password:</label>
                <input type="password" id="password" name="password" required>
                <p id="passwordError" class="error-message"></p>
            </div>
        
            <div class="form-group">
                <label for="phone">Phone Number:</label>
                <input type="text" id="phone" name="phone" required>
                <p id="phoneError" class="error-message"></p>
            </div>
        
            <button type="submit">Register</button>
        </form>
        

        <p id="message"></p>
    </div>

    <script>
        const form = document.getElementById("registrationForm");

        form.addEventListener("submit", async (e) => {
            e.preventDefault();

            const formData = new FormData(form);
            const response = await fetch(form.action, {
                method: "POST",
                body: formData,
            });

            // Hide previous error messages
            document.getElementById("usernameError").style.display = "none";
            document.getElementById("emailError").style.display = "none";
            document.getElementById("passwordError").style.display = "none";

            if (response.redirected) {
                window.location.href = response.url; // Redirect to the login page
            } else {
                const errorText = await response.text();
                const messageLines = errorText.split("\n");

                messageLines.forEach((line) => {
                    if (line.includes("Invalid username")) {
                        document.getElementById("usernameError").textContent = line;
                        document.getElementById("usernameError").style.display = "block";
                    }
                    if (line.includes("Invalid email format")) {
                        document.getElementById("emailError").textContent = line;
                        document.getElementById("emailError").style.display = "block";
                    }
                    if (line.includes("Invalid password")) {
                        document.getElementById("passwordError").textContent = line;
                        document.getElementById("passwordError").style.display = "block";
                    }
                    if (line.includes("Invalid phone number")) {
    document.getElementById("phoneError").textContent = line;
    document.getElementById("phoneError").style.display = "block";
}

                    if (line.includes("Username is already taken")) {
                        alert("Username already taken. Please try another.");
                    }
                    if (line.includes("Email is already registered")) {
                        alert("This email is already registered. Please login.");
                    }
                });
            }
        });

    </script>
</body>

</html>



//script.js:---------------------------------------------------------------------------------------------------------------------
// Declare global variables
let searchTerm = '';
let displayedPhones = [];

// Fetching the phone data from the backend
async function getPhones() {
    try {
        const response = await fetch('/phones');
        const phones = await response.json();

        if (phones.length > 0) {
            displayedPhones = phones.map(phone => ({
                ...phone,
                productLink: phone.productLink || '#' // Use a default link if not provided
            }));
            displayPhones(displayedPhones);
            setupFilters(displayedPhones);
        } else {
            alert("No phones available");
        }
    } catch (error) {
        console.error('Error fetching phones:', error);
        alert("Failed to load phone data");
    }
}

// Displaying the phones in the UI
function displayPhones(phones) {
    const phoneListContainer = document.getElementById('phoneListContainer');
    phoneListContainer.innerHTML = '';

    if (phones.length > 0) {
        phones.forEach(phone => {
            const phoneItem = document.createElement('div');
            phoneItem.className = 'phone-item';
            phoneItem.dataset.company = phone.company;
            phoneItem.innerHTML = `
                <img src="${phone.imageUrl}" alt="${phone.model}" />
                <p><a href="${phone.productLink}" target="_blank">${phone.model}</a></p>
                <p>$${phone.price}</p>
            `;
            phoneListContainer.appendChild(phoneItem);
        });
    } else {
        phoneListContainer.innerHTML = `<p>No phones found matching your search.</p>`;
    }
}

function isAlphanumeric(str) {
    return /^[a-zA-Z0-9\s+-]+$/.test(str);
}

// Function to handle the search and frequency count
async function handleSearchAndCount(event) {

    // This is part of your existing method
    if (event.key === 'Enter' || event.target.value === '') {

        searchTerm = document.getElementById('searchInput').value.trim().toLowerCase();

        if (searchTerm === "") {
            getPhones();
            hideSearchInfo(); // Hide search frequency and suggestions
            return;
        }

        if (!isAlphanumeric(searchTerm)) {
            alert("Please enter only alphanumeric characters.");
            return;
        }

        try {
            // Fetch phones based on search term
            const phoneResponse = await fetch(`/phones/search?model=${encodeURIComponent(searchTerm)}`);
            const phones = await phoneResponse.json();

            // Fetch frequency count
            const frequencyResponse = await fetch('/phones/searchFrequency', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ searchTerm })
            });
            const frequencyCount = await frequencyResponse.json();

            // Display frequency count
            document.getElementById('frequencyDisplay').style.display = 'block';
            document.getElementById('frequencyDisplay').textContent = `🔎Search Frequency: ${frequencyCount}`;

            if (phones.length > 0) {
                displayedPhones = phones;
                displayPhones(phones);
                document.getElementById('suggestionsContainer').style.display = 'none';
            } else {
                // Fetch spelling suggestions if no phones found
                const suggestionsResponse = await fetch(`/phones/spellcheck?searchTerm=${encodeURIComponent(searchTerm)}`);
                const suggestions = await suggestionsResponse.json();

                if (suggestions.length > 0) {
                    document.getElementById('suggestionsContainer').style.display = 'block';
                    document.getElementById('suggestionsContainer').innerHTML = `Suggested word: ${suggestions[0]}`;

                    // Fetch phones for the suggested word
                    const suggestedPhoneResponse = await fetch(`/phones/search?model=${encodeURIComponent(suggestions[0])}`);
                    const suggestedPhones = await suggestedPhoneResponse.json();
                    displayedPhones = suggestedPhones;
                    displayPhones(suggestedPhones);
                } else {
                    displayedPhones = [];
                    displayPhones([]);
                    document.getElementById('suggestionsContainer').style.display = 'none';
                }
            }
        } catch (error) {
            console.error('Error handling search and count:', error);
            alert("Failed to fetch search results or frequency count");
        }
    }
}

function hideSearchInfo() {
    document.getElementById('frequencyDisplay').style.display = 'none';
    document.getElementById('suggestionsContainer').style.display = 'none';
}

// Function to filter the phones based on the selected company
function filterPhones() {
    const companyFilter = document.getElementById('companyFilter');
    const selectedCompany = companyFilter.value.trim().toLowerCase();

    const filteredPhones = displayedPhones.filter(phone =>
        (selectedCompany === '' || phone.company.toLowerCase() === selectedCompany) &&
        phone.model.toLowerCase().includes(searchTerm)
    );

    displayPhones(filteredPhones);
}

// Function to setup company filters in the dropdown
function setupFilters(phones) {
    const companyFilter = document.getElementById('companyFilter');
    const companies = [...new Set(phones.map(phone => phone.company))];

    companyFilter.innerHTML = '<option value="">Select Company</option>';

    companies.forEach(company => {
        const option = document.createElement('option');
        option.value = company;
        option.textContent = company;
        companyFilter.appendChild(option);
    });

    companyFilter.addEventListener('change', filterPhones);
}

// Function to apply sorting based on selected sort option
function applySort() {
    const sortFilter = document.getElementById('sortFilter');
    const sortOption = sortFilter.value;
    const companyFilter = document.getElementById('companyFilter');
    const selectedCompany = companyFilter.value.trim().toLowerCase();

    if (displayedPhones.length === 0) return; // If no phones are displayed, do nothing

    try {
        // First, filter by company if one is selected
        let phonesToSort = selectedCompany
            ? displayedPhones.filter(phone => phone.company.toLowerCase() === selectedCompany)
            : displayedPhones;

        // Then apply sorting
        if (sortOption === 'priceLowHigh') {
            phonesToSort.sort((a, b) => a.price - b.price);
        } else if (sortOption === 'priceHighLow') {
            phonesToSort.sort((a, b) => b.price - a.price);
        } else if (sortOption === 'modelAZ') {
            phonesToSort.sort((a, b) => a.model.localeCompare(b.model));
        } else if (sortOption === 'modelZA') {
            phonesToSort.sort((a, b) => b.model.localeCompare(a.model));
        }

        // Re-display the phones after sorting
        displayPhones(phonesToSort);

    } catch (error) {
        console.error('Error applying sort:', error);
        alert("Failed to sort phones");
    }
}

// Modify the filterPhones function to also apply current sort
function filterPhones() {
    const companyFilter = document.getElementById('companyFilter');
    const selectedCompany = companyFilter.value.trim().toLowerCase();

    const filteredPhones = displayedPhones.filter(phone =>
        (selectedCompany === '' || phone.company.toLowerCase() === selectedCompany) &&
        phone.model.toLowerCase().includes(searchTerm)
    );

    // Apply current sort to filtered phones
    const sortFilter = document.getElementById('sortFilter');
    const currentSort = sortFilter.value;

    if (currentSort) {
        applySort(); // This will now use the filtered phones
    } else {
        displayPhones(filteredPhones);
    }
}

function resetAllFilters() {
    // Reset search input
    document.getElementById('searchInput').value = '';

    // Reset company filter dropdown to default
    document.getElementById('companyFilter').selectedIndex = 0;

    // Reset sort filter dropdown to default
    document.getElementById('sortFilter').selectedIndex = 0;

    // Reset search term and displayed phones
    searchTerm = '';

    // Reload all phones
    getPhones();

    // Hide frequency and suggestions displays
    document.getElementById('frequencyDisplay').style.display = 'none';
    document.getElementById('suggestionsContainer').style.display = 'none';
}

// Update event listeners
document.addEventListener('DOMContentLoaded', () => {
    getPhones();

    const searchInputField = document.getElementById('searchInput');
    searchInputField.addEventListener('keydown', handleSearchAndCount);
    searchInputField.addEventListener('input', function(event) {
        if (event.target.value === '') {
            getPhones();
        } else if (!isValidInput(event.target.value)) {
            alert("Please enter only alphanumeric characters, spaces, plus (+), or minus (-).");
            event.target.value = event.target.value.replace(/[^a-zA-Z0-9\s+-]/g, '');
        }

        // Trigger word completion on input change
        handleWordCompletion();
    });

    const companyFilter = document.getElementById('companyFilter');
    companyFilter.addEventListener('change', filterPhones);

    const sortFilter = document.getElementById('sortFilter');
    sortFilter.addEventListener('change', applySort);
});

// Function to validate input
function isValidInput(input) {
    return /^[a-zA-Z0-9\s+-]*$/.test(input); // Validates alphanumeric characters, spaces, plus, and minus
}


// Handling word completion and displaying suggestions
async function handleWordCompletion() {
    console.log("handleWordCompletion function triggered");

    const searchInput = document.getElementById('searchInput').value.trim();

    if (searchInput.length > 0) {
        try {
            console.log("Fetching word completion for:", searchInput);
            const response = await fetch(`/phones/word-completion?prefix=${encodeURIComponent(searchInput)}`);

            if (response.ok) {
                const suggestions = await response.json();
                console.log("Suggestions received:", suggestions);

                if (suggestions && suggestions.length > 0) {
                    displaySuggestions(suggestions);
                } else {
                    document.getElementById('suggestionsContainer').style.display = 'none';
                }
            } else {
                console.error("Error fetching suggestions:", response.statusText);
            }
        } catch (error) {
            console.error("Error fetching word completion suggestions:", error);
        }
    } else {
        document.getElementById('suggestionsContainer').style.display = 'none';
    }
}

function displaySuggestions(suggestions) {
    console.log("Displaying suggestions:", suggestions);
    const suggestionsContainer = document.getElementById('suggestionsContainer');
    suggestionsContainer.innerHTML = '';

    if (suggestions.length > 0) {
        suggestions.forEach(suggestion => {
            const suggestionItem = document.createElement('div');
            suggestionItem.textContent = suggestion;
            suggestionItem.classList.add('suggestion-item');

            suggestionItem.onclick = () => {
                document.getElementById('searchInput').value = suggestion;
                suggestionsContainer.style.display = 'none';
                handleSearchAndCount({ key: 'Enter' });
            };

            suggestionsContainer.appendChild(suggestionItem);
        });

        suggestionsContainer.style.display = 'block';
    } else {
        suggestionsContainer.style.display = 'none';
    }
}
async function displayDatabaseWordCount() {
    // Log the data (you can remove this if not needed for debugging)
    console.log("Data");

    // Get the search term from the input field and remove leading/trailing spaces
    const searchTerm = document.getElementById('searchInput').value.trim();

    // Get the element where we want to display the word count
    const displayElement = document.getElementById('databaseWordCountDisplay');

    // Check if the search term is not empty
    if (searchTerm.length > 0) {
        try {
            // Send a GET request to the server with the search term as a query parameter
            const response = await fetch(`/phones/database-word-count?term=${encodeURIComponent(searchTerm)}`);

            // If the response is successful (status code 200)
            if (response.ok) {
                // Parse the JSON response to get the word count
                const count = await response.json();

                // Update the display with the word count
                displayElement.textContent = `🗂️Database occurrences: ${count}`;
                displayElement.style.display = 'inline-block'; // Make the display element visible
            } else {
                // If the response was not successful, log an error and hide the display element
                console.error('Failed to fetch database word count');
                displayElement.style.display = 'none'; // Hide the element in case of failure
            }
        } catch (error) {
            // If there's an error (e.g., network issues), log it and hide the display element
            console.error('Error:', error);
            displayElement.style.display = 'none'; // Hide the element in case of error
        }
    } else {
        // If the search term is empty, clear the display element and hide it
        displayElement.textContent = '';
        displayElement.style.display = 'none'; // Hide the element when there's no search term
    }
}



//style.css:-------------------------------------------------------------------------------------------------------------
/* General Body Styling */
body {
    font-family: 'Arial', sans-serif;
    background-color: #f3f4f6;
    margin: 0;
    padding: 20px;
    text-align: center;
    line-height: 1.6;
}

/* Header Styling */
header {
    background-color: #1e3a8a;
    color: #ffffff;
    padding: 20px;
    border-radius: 10px;
    margin-bottom: 20px;
    font-size: 24px;
    text-shadow: 1px 1px 4px #000000;
}

/* Navigation Bar Styling */
nav {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 20px;
    background-color: #ffffff;
    border-radius: 5px;
    margin-bottom: 20px;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.05);
}

/* Navbar List */
#navbar {
    display: flex;
    list-style: none;
    gap: 20px;
}

#navbar li {
    cursor: pointer;
    padding: 10px;
    color: #1e3a8a;
    font-size: 20px;
    font-weight: bold;
    transition: color 0.3s ease;
}

#navbar li:hover {
    color: #2563eb;
}

/* Filters Section Styling (Search and Company Filter) */
.filters {
    display: flex;
    justify-content: flex-start;
    align-items: center;
    gap: 10px;
    margin-bottom: 20px;
}

#frequencyDisplay {
    font-weight: bold;
    color: #2563eb;
    font-size: 16px;
    display: none; /* Initially hidden */
}
#suggestionsContainer {
    font-weight: bold;
    color: #2563eb;
    font-size: 16px;
    display: none; /* Initially hidden */
    display: none; /* Initially hidden */

}
#searchInput, #companyFilter, #sortFilter {
    padding: 10px;
    font-size: 16px;
    width: 250px; /* Match the width of the search bar */
    border-radius: 5px;
    border: 1px solid #ddd;
    outline: none;
    transition: border 0.3s ease;
}

#searchInput:focus, #companyFilter:focus, #sortFilter:focus {
    border-color: #2563eb;
    box-shadow: 0 0 8px rgba(37, 99, 235, 0.2);
}

/* Phone List Styling */
.phone-list-container {
    margin-top: 20px;
}

.phone-list {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 20px;
}

.phone-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 250px;
    background-color: #ffffff;
    margin: 20px;
    padding: 15px;
    border: 1px solid #ddd;
    border-radius: 10px;
    text-align: center;
    cursor: pointer;
    transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.phone-item:hover {
    transform: translateY(-5px);
    box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
}

.phone-item img {
    width: 120px;
    height: auto;
    object-fit: contain;
    margin-bottom: 10px;
}

.phone-details h3 {
    margin: 10px 0;
    color: #1e3a8a;
}

.phone-details p {
    color: #555;
    margin: 5px 0;
}

/* Align the Register and Login buttons to the right */
.auth-links {
    margin-left: auto; /* Push the Register and Login to the far right */
    list-style: none;
    display: flex;
    gap: 10px; /* Space between Register and Login */
}

/* Styling for the Register and Login buttons */
.auth-links li a {
    text-decoration: none;
    padding: 10px;
    color: #1e3a8a;
    font-weight: bold;
    transition: color 0.3s ease;
}

/* Hover effect for Register and Login buttons */
.auth-links li a:hover {
    color: #2563eb;
}

/* Button Styling */
.button {
    padding: 10px 20px;
    background-color: #1e3a8a;
    color: white;
    border: none;
    border-radius: 5px;
    cursor: pointer;
    font-weight: bold;
    transition: background-color 0.3s ease, transform 0.2s ease;
}

.button:hover {
    background-color: #2563eb;
    transform: translateY(-3px);
}

/* Responsive Design */
@media screen and (max-width: 768px) {
    .filters {
        flex-direction: column;
        gap: 10px;
    }

    .phone-list {
        flex-direction: column;
        align-items: center;
    }

    .phone-item {
        width: 90%;
    }
}

/* Comparison Table Styling */
.comparison-container {
    max-width: 1000px;
    margin: 40px auto;
    padding: 20px;
    background-color: #ffffff;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
    border-radius: 10px;
}

table {
    width: 100%;
    border-collapse: collapse;
    margin: 20px 0;
}

table thead th {
    background-color: #3b82f6;
    color: white;
    padding: 15px;
    text-transform: uppercase;
    font-weight: 600;
}

table th, table td {
    padding: 15px;
    text-align: center;
    border: 1px solid #d1d5db;
}

table tbody tr:nth-child(even) {
    background-color: #f9fafb;
}

table tbody tr:hover {
    background-color: #e0f2fe;
}

.comparison-header {
    color: #1e3a8a;
    margin-bottom: 20px;
}

/* Scrollbar Styling */
::-webkit-scrollbar {
    width: 8px;
}

::-webkit-scrollbar-track {
    background: #f3f4f6;
}

::-webkit-scrollbar-thumb {
    background: #1e3a8a;
    border-radius: 5px;
}

/* Popup Container */
.popup-container {
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    background-color: #ffffff;
    padding: 20px;
    border-radius: 10px;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
    z-index: 9999;
    display: none;
}

.popup-content {
    display: flex;
    flex-direction: column;
    align-items: center;
}

.popup-close {
    align-self: flex-end;
    font-size: 20px;
    cursor: pointer;
    color: #1e3a8a;
}

/* Footer Styling */
footer {
    margin-top: 40px;
    padding: 20px;
    background-color: #1e3a8a;
    color: white;
    text-align: center;
    border-radius: 10px;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.05);
}

.footer-content {
    display: flex;
    justify-content: center;
    gap: 20px;
    flex-wrap: wrap;
}

.features-list {
    list-style: none;
    padding: 0;
    color: #ffffff;
}

.features-list li {
    margin: 5px 0;
}

/* Make the navbar a flex container */
nav {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 20px;
    background-color: #ffffff;
    border-radius: 5px;
    margin-bottom: 20px;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.05);
}

/* Style for the main navbar items */
#navbar {
    display: flex;
    list-style: none;
    gap: 20px; /* Space out the items */
    flex-grow: 1; /* Allow the navbar to grow and take available space */
}

/* Align the Register and Login buttons to the right */
.auth-links {
    margin-left: auto; /* Push the Register and Login to the far right */
    list-style: none;
    display: flex;
    gap: 10px; /* Space between Register and Login */
}

/* Styling for the Register and Login buttons */
.auth-links li a {
    text-decoration: none;
    padding: 10px;
    color: #1e3a8a;
    font-weight: bold;
    transition: color 0.3s ease;
}

/* Hover effect for Register and Login buttons */
.auth-links li a:hover {
    color: #2563eb;
}

/* Login and Registration Form Container */
.login-container, .registration-container {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh; /* Full screen height */
}

.login-box, .registration-box {
    background-color: #ffffff;
    padding: 30px;
    border-radius: 10px;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
    width: 100%;
    max-width: 400px;
    text-align: center;
}

h2 {
    margin-bottom: 20px;
    color: #1e3a8a;
}

/* Form Inputs Styling */
.textbox input {
    width: 100%;
    padding: 10px;
    margin-bottom: 20px;
    border-radius: 5px;
    border: 1px solid #ddd;
    font-size: 16px;
}

.textbox input:focus {
    border-color: #2563eb;
    box-shadow: 0 0 8px rgba(37, 99, 235, 0.2);
}

/* Text Links Styling */
.link-button {
    background: none;
    border: none;
    color: #1e3a8a;
    text-decoration: underline;
    font-size: 16px;
    cursor: pointer;
    font-weight: bold;
    transition: color 0.3s ease;
}

.link-button:hover {
    color: #2563eb;
}

.footer-links {
    margin-top: 10px;
}

.footer-links a {
    color: #2563eb;
    text-decoration: none;
    font-size: 14px;
}

.footer-links a:hover {
    text-decoration: underline;
}

.link-text {
    color: #1e3a8a;
    text-decoration: none;
    font-size: 14px;
}

.link-text:hover {
    color: #2563eb;
}

a{
    text-decoration: none;
    color: #000000;
}

#compareContainer {
    max-width: 800px;
    margin: 0 auto;
    padding: 20px;
}

#phoneSelectionContainer {
    display: flex;
    justify-content: space-between;
    margin-bottom: 20px;
}

#phone1Select, #phone2Select {
    width: 40%;
    padding: 10px;
    font-size: 16px;
}

#compareButton {
    width: 15%;
    padding: 10px;
    font-size: 16px;
    background-color: #4CAF50;
    color: white;
    border: none;
    cursor: pointer;
}

#compareButton:hover {
    background-color: #45a049;
}

#comparisonResultContainer {
    border: 1px solid #ddd;
    padding: 20px;
}

table {
    width: 100%;
    border-collapse: collapse;
}

th, td {
    border: 1px solid #ddd;
    padding: 8px;
    text-align: left;
}

th {
    background-color: #f2f2f2;
}
/* Parent container for proper positioning */
.filters {
    position: relative; /* Ensures child elements are positioned relative to this container */
}


 #suggestionsContainer {
    position: absolute;
    top: 100%; /* Position it below the search input */
    left: 0;
    width: 20%;
    background-color: white;
    border: 1px solid #ddd;
    border-radius: 5px;
    z-index: 1000; /* Ensure it's above other elements */
    max-height: 200px; /* Add a maximum height if needed */
    overflow-y: auto;
}


 /* Parent Container */
 .filters {
     position: relative; /* Set parent container as relative for absolute positioning of suggestions */
 }
  /* Suggestion Items (Optional Styling) */
 .suggestion-item {
     padding: 8px;
     cursor: pointer;
 }

 .suggestion-item:hover {
     background-color: #f0f0f0;
 }
.suggestion-item {
    padding: 8px;
    margin-top: 10px;
    cursor: pointer;
}

.suggestion-item:hover {
    background-color: #f0f0f0;
}


#frequencyDisplay {
    font-size: 16px;
    padding: 8px;
    background-color: #f1f1f1;
    border: 1px solid #ddd;
    margin-top: 10px;
    text-align: center;
}


//application.properties:-----------------------------------------------------------------------------------------------
spring.application.name=phone-comparison-backend
spring.datasource.url=jdbc:mysql://localhost:3306/phone_db
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true



users.csv:---------------------------------------------------------------------------------------------------------------
isha,isha123@gmail.com,Hello@123,+13456782345
iamisha,hello@gmail.com,HelloIsha@123,+13456782678
dcfv,i@gmail.com,H345678hbdcD,+12345678567
sdfghj,sdfghj@rgthj.com,Hwertyujhbv45678,+12346784567
sdfghjghjnkm,sdfghctvghnjj@rgthj.com,Hwqergfvc#245,+1 456 567 4578
isha457,isha7896@gmail.com,Hello@123,+16748345789
dfghjk,sdfghjk@gmail.com,Gwertyu3456789,+19898443706
rfyh,dfhjkl@sdjk.com,Isgah@1243,+12345673456



pom.xml:----------------------------------------------------------------------------------------------------------------------
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.3.5</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.example</groupId>
	<artifactId>phone-comparison-backend</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>phone-comparison-backend</name>
	<description>Demo project for Spring Boot</description>
	<url/>
	<licenses>
		<license/>
	</licenses>
	<developers>
		<developer/>
	</developers>
	<scm>
		<connection/>
		<developerConnection/>
		<tag/>
		<url/>
	</scm>
	<properties>
		<java.version>17</java.version>
	</properties>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>

		</dependency>

		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
			<version>2.1.0</version>
		</dependency>

		<dependency>
			<groupId>org.apache.poi</groupId>
			<artifactId>poi-ooxml</artifactId>
			<version>5.2.2</version>
		</dependency>
		<dependency>
			<groupId>mysql</groupId>
			<artifactId>mysql-connector-java</artifactId>
			<version>8.0.26</version> <!-- Make sure to use the latest version -->
		</dependency>

		<dependency>
			<groupId>com.opencsv</groupId>
			<artifactId>opencsv</artifactId>
			<version>5.7.1</version>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-devtools</artifactId>
			<scope>runtime</scope>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>


	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>

</project>




//exception:
//GlobalExceptionHandler.java:-----------------------------------------------------------------------------------------------
package com.example.phone_comparison_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        return new ResponseEntity<>("An error occurred: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}


