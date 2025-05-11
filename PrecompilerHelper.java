import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fully compatible Trie precompiler helper
 */
public class PrecompilerHelper {
    
    // Dictionary type constants
    public static final String TYPE_CHARS = "chars";
    public static final String TYPE_BASE = "base";
    
    // Memory usage for each dictionary type
    private Map<String, Long> typeMemoryUsage = new HashMap<>();
    
    // Loaded entry count for each dictionary type
    private Map<String, Integer> typeLoadedCount = new HashMap<>();
    
    // Version hash for each dictionary type
    private Map<String, String> typeVersionHash = new HashMap<>();
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java PrecompilerHelper <output_directory>");
            System.out.println("Example: java PrecompilerHelper app/src/main/assets/dict");
            System.exit(1);
        }
        
        String outputDir = args[0];
        PrecompilerHelper helper = new PrecompilerHelper();
        
        try {
            // Build Trie tree
            System.out.println("Starting tree building process");
            TrieNode root = new TrieNode();
            int totalWords = 0;
            
            // Load base dictionary first because it's more important
            System.out.println("Processing dictionary: cn_dicts/base.dict.yaml");
            int baseCount = helper.buildFromDictFile("cn_dicts/base.dict.yaml", TYPE_BASE, root);
            totalWords += baseCount;
            
            // Then load chars dictionary
            System.out.println("Processing dictionary: cn_dicts/chars.dict.yaml");
            int charsCount = helper.buildFromDictFile("cn_dicts/chars.dict.yaml", TYPE_CHARS, root);
            totalWords += charsCount;
            
            System.out.println("Total words loaded: " + totalWords);
            
            // Generate precompiled files
            System.out.println("Generating serialized tree files...");
            helper.generateTrieFiles(outputDir, root);
            
        } catch (Exception e) {
            System.err.println("Failed to build tree: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Build Trie tree from dictionary file
     */
    private int buildFromDictFile(String filename, String type, TrieNode root) throws IOException {
        System.out.println("Reading dictionary file: " + filename);
        int count = 0;
        long startTime = System.currentTimeMillis();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    String word = parts[0];
                    int frequency = 0;
                    
                    try {
                        frequency = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) {
                        frequency = 1;
                    }
                    
                    insertWord(root, word, frequency);
                    count++;
                    
                    if (count % 10000 == 0) {
                        System.out.println(String.format("Loaded %d entries for %s", count, type));
                    }
                }
            }
        }
        
        // Estimate memory usage
        int avgWordLength = 2; // Assume average word length is 2 characters
        int bytesPerWord = (avgWordLength * 2) + 8; // chars + node overhead + frequency
        long memoryUsage = bytesPerWord * count;
        
        // Record data
        typeMemoryUsage.put(type, memoryUsage);
        typeLoadedCount.put(type, count);
        typeVersionHash.put(type, "precompiled-" + System.currentTimeMillis());
        
        long endTime = System.currentTimeMillis();
        System.out.println(String.format(
                "Dictionary %s loaded: %d entries, time: %dms", type, count, (endTime - startTime)));
        
        return count;
    }
    
    /**
     * Insert word into Trie tree
     */
    private void insertWord(TrieNode root, String word, int frequency) {
        TrieNode current = root;
        
        for (char c : word.toCharArray()) {
            current.children.putIfAbsent(c, new TrieNode());
            current = current.children.get(c);
        }
        
        current.isEnd = true;
        current.frequency = frequency;
        current.word = word;
    }
    
    /**
     * Generate precompiled files
     */
    private void generateTrieFiles(String outputDir, TrieNode root) throws IOException {
        File outDir = new File(outputDir);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        
        System.out.println("Saving files to: " + outDir.getAbsolutePath());
        
        // Prepare serializable tree
        System.out.println("Creating serializable tree...");
        List<WordEntry> allWords = new ArrayList<>();
        collectWords(root, allWords);
        
        // Create complete serializable tree
        SerializableTrieTree tree = new SerializableTrieTree();
        
        System.out.println("Building serializable tree with " + allWords.size() + " words");
        for (WordEntry entry : allWords) {
            tree.insert(entry.word, entry.frequency);
        }
        
        // Set loaded flag
        tree.isLoaded = true;
        
        // Save Trie tree
        File trieFile = new File(outDir, "precompiled_trie.bin");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(trieFile))) {
            out.writeObject(tree);
        }
        
        // Save memory usage information
        File memoryFile = new File(outDir, "memory_usage.bin");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(memoryFile))) {
            out.writeObject(typeMemoryUsage);
        }
        
        // Save version information
        File versionFile = new File(outDir, "dictionary_versions.bin");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(versionFile))) {
            out.writeObject(typeVersionHash);
        }
        
        // Save JSON info file
        createInfoJsonFile(outDir, allWords.size());
        
        System.out.println("All files saved successfully");
        System.out.println("Tree file size: " + formatFileSize(trieFile.length()));
    }
    
    /**
     * Collect all words
     */
    private void collectWords(TrieNode node, List<WordEntry> words) {
        if (node.isEnd && node.word != null) {
            words.add(new WordEntry(node.word, node.frequency));
        }
        
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            collectWords(entry.getValue(), words);
        }
    }
    
    /**
     * Create info JSON file
     */
    private void createInfoJsonFile(File dir, int totalWords) throws IOException {
        File infoFile = new File(dir, "dict_info.json");
        StringBuilder info = new StringBuilder();
        info.append("{\n");
        info.append("  \"exportTime\": \"").append(
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date())).append("\",\n");
        info.append("  \"totalWords\": ").append(totalWords).append(",\n");
        info.append("  \"dictTypes\": [\n");
        
        List<String> types = new ArrayList<>(typeMemoryUsage.keySet());
        for (int i = 0; i < types.size(); i++) {
            String type = types.get(i);
            long memory = typeMemoryUsage.get(type);
            int count = typeLoadedCount.get(type);
            String version = typeVersionHash.get(type);
            
            info.append("    {\n");
            info.append("      \"type\": \"").append(type).append("\",\n");
            info.append("      \"count\": ").append(count).append(",\n");
            info.append("      \"memory\": \"").append(formatFileSize(memory)).append("\",\n");
            info.append("      \"version\": \"").append(version).append("\"\n");
            info.append("    }");
            
            if (i < types.size() - 1) {
                info.append(",");
            }
            info.append("\n");
        }
        
        info.append("  ]\n");
        info.append("}\n");
        
        try (FileOutputStream out = new FileOutputStream(infoFile)) {
            out.write(info.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * Format file size
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
    
    /**
     * Internal Trie node class
     */
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEnd = false;
        int frequency = 0;
        String word = null;
    }
    
    /**
     * Word entry data class
     */
    private static class WordEntry {
        String word;
        int frequency;
        
        WordEntry(String word, int frequency) {
            this.word = word;
            this.frequency = frequency;
        }
    }
    
    /**
     * Serializable Trie node - must match the application code exactly
     */
    public static class SerializableTrieNode implements Serializable {
        static final long serialVersionUID = 1L;
        
        // Must match the exact field type in Kotlin - Kotlin's Char corresponds to Java's Character
        HashMap<Character, SerializableTrieNode> children = new HashMap<>();
        boolean isEnd = false;
        int frequency = 0;
        String word = null;
    }
    
    /**
     * Serializable Trie tree - must match the application code exactly
     */
    public static class SerializableTrieTree implements Serializable {
        static final long serialVersionUID = 1L;
        
        // Force field access modifiers to match Kotlin
        private SerializableTrieNode root = new SerializableTrieNode();
        public boolean isLoaded = false;
        
        /**
         * Insert a word - provide a method consistent with Kotlin version
         */
        public void insert(String word, int frequency) {
            if (word == null || word.isEmpty()) return;
            
            SerializableTrieNode current = root;
            for (char c : word.toCharArray()) {
                if (!current.children.containsKey(c)) {
                    current.children.put(c, new SerializableTrieNode());
                }
                current = current.children.get(c);
            }
            current.isEnd = true;
            current.frequency = frequency;
            current.word = word;
        }
    }
} 