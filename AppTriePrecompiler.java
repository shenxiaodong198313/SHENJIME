import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * App-compatible Trie Tree Precompiler
 */
public class AppTriePrecompiler {
    
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
            System.out.println("Usage: java AppTriePrecompiler <output_directory>");
            System.out.println("Example: java AppTriePrecompiler app/src/main/assets/dict");
            System.exit(1);
        }
        
        String outputDir = args[0];
        AppTriePrecompiler precompiler = new AppTriePrecompiler();
        
        try {
            // Build Trie tree root node
            TrieNode root = new TrieNode();
            int totalWords = 0;
            
            // Process chars.dict.yaml
            System.out.println("Processing dictionary: cn_dicts/chars.dict.yaml");
            int charsCount = precompiler.buildFromDictFile("cn_dicts/chars.dict.yaml", TYPE_CHARS, root);
            totalWords += charsCount;
            
            // Process base.dict.yaml
            System.out.println("Processing dictionary: cn_dicts/base.dict.yaml");
            int baseCount = precompiler.buildFromDictFile("cn_dicts/base.dict.yaml", TYPE_BASE, root);
            totalWords += baseCount;
            
            System.out.println("Total loaded entries: " + totalWords);
            
            // Save precompiled tree
            precompiler.savePrecompiledTrie(new File(outputDir), root);
            System.out.println("Precompiled Trie tree saved to: " + outputDir);
        } catch (Exception e) {
            System.err.println("Failed to build precompiled tree: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Build Trie tree from dictionary file
     */
    public int buildFromDictFile(String dictFileName, String dictType, TrieNode root) throws IOException {
        System.out.println("Building Trie tree from dictionary file: " + dictFileName);
        
        int loadedCount = 0;
        long startTime = System.currentTimeMillis();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(dictFileName), "UTF-8"))) {
            
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
                    
                    insert(root, word, frequency);
                    loadedCount++;
                    
                    if (loadedCount % 10000 == 0) {
                        System.out.println(String.format("Loaded %d entries, type: %s", loadedCount, dictType));
                    }
                }
            }
        }
        
        // Estimate memory usage
        int averageWordLength = 2;  // Assume average word length is 2
        int bytesPerWord = (averageWordLength * 2) + 8;  // chars + node overhead + frequency
        long memoryUsage = bytesPerWord * loadedCount;
        
        // Save statistics
        typeMemoryUsage.put(dictType, memoryUsage);
        typeLoadedCount.put(dictType, loadedCount);
        typeVersionHash.put(dictType, "precompiled-" + System.currentTimeMillis());
        
        long endTime = System.currentTimeMillis();
        System.out.println(String.format(
                "Dictionary type %s build completed, %d entries, time: %d ms",
                dictType, loadedCount, (endTime - startTime)));
        
        return loadedCount;
    }
    
    /**
     * Insert a word into Trie tree
     */
    private void insert(TrieNode root, String word, int frequency) {
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
     * Save precompiled Trie tree to file
     */
    public void savePrecompiledTrie(File outputDir, TrieNode root) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        System.out.println("Saving precompiled Trie tree to: " + outputDir.getAbsolutePath());
        
        // Create serializable tree
        SerializableTrieTree serializableTree = new SerializableTrieTree();
        constructSerializableTree(root, serializableTree);
        serializableTree.isLoaded = true;
        
        // Save tree to file
        File trieFile = new File(outputDir, "precompiled_trie.bin");
        ObjectOutputStream trieOut = new ObjectOutputStream(new FileOutputStream(trieFile));
        trieOut.writeObject(serializableTree);
        trieOut.close();
        
        // Save memory usage information
        File memoryFile = new File(outputDir, "memory_usage.bin");
        ObjectOutputStream memoryOut = new ObjectOutputStream(new FileOutputStream(memoryFile));
        memoryOut.writeObject(typeMemoryUsage);
        memoryOut.close();
        
        // Save dictionary version information
        File versionFile = new File(outputDir, "dictionary_versions.bin");
        ObjectOutputStream versionOut = new ObjectOutputStream(new FileOutputStream(versionFile));
        versionOut.writeObject(typeVersionHash);
        versionOut.close();
        
        // Export dictionary information file (JSON format for easy inspection)
        File infoFile = new File(outputDir, "dict_info.json");
        StringBuilder info = new StringBuilder();
        info.append("{\n");
        info.append("  \"exportTime\": \"").append(
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date())).append("\",\n");
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
        
        FileOutputStream infoOut = new FileOutputStream(infoFile);
        infoOut.write(info.toString().getBytes());
        infoOut.close();
        
        System.out.println("Precompiled Trie tree saved successfully, file size: " + formatFileSize(trieFile.length()));
    }
    
    /**
     * Convert runtime tree to serializable tree
     */
    private void constructSerializableTree(TrieNode root, SerializableTrieTree serializableTree) {
        collectAndInsert(root, serializableTree);
    }
    
    /**
     * Collect all words and insert into serializable tree
     */
    private void collectAndInsert(TrieNode node, SerializableTrieTree serializableTree) {
        if (node.isEnd && node.word != null) {
            serializableTree.insert(node.word, node.frequency);
        }
        
        // Recursively process child nodes
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            collectAndInsert(entry.getValue(), serializableTree);
        }
    }
    
    /**
     * Format file size to human readable string
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
    
    /**
     * Trie tree node class
     */
    public static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEnd = false;
        int frequency = 0;
        String word = null;
    }
    
    /**
     * Serializable Trie node structure
     */
    public static class SerializableTrieNode implements Serializable {
        private static final long serialVersionUID = 1L;
        
        // HashMap to store child nodes
        HashMap<Character, SerializableTrieNode> children = new HashMap<>();
        // Flag for word end
        boolean isEnd = false;
        // Frequency for sorting
        int frequency = 0;
        // Store the complete word only in leaf nodes to reduce memory redundancy
        String word = null;
    }
    
    /**
     * Serializable Trie tree implementation for persistent storage
     */
    public static class SerializableTrieTree implements Serializable {
        private static final long serialVersionUID = 1L;
        
        // Root node
        private SerializableTrieNode root = new SerializableTrieNode();
        
        // Loading status flag
        boolean isLoaded = false;
        
        /**
         * Insert a word
         */
        public void insert(String word, int frequency) {
            if (word == null || word.isEmpty()) return;
            
            SerializableTrieNode current = root;
            for (char c : word.toCharArray()) {
                current.children.putIfAbsent(c, new SerializableTrieNode());
                current = current.children.get(c);
            }
            current.isEnd = true;
            current.frequency = frequency;
            current.word = word;
        }
    }
} 