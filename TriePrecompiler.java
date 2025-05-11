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
 * Trie Tree Precompiler
 * Standalone Java tool to build precompiled Trie tree from dictionary files
 */
public class TriePrecompiler {
    
    // Dictionary type constants
    public static final String TYPE_CHARS = "chars";
    public static final String TYPE_BASE = "base";
    
    // Memory usage for each dictionary type
    private Map<String, Long> typeMemoryUsage = new HashMap<>();
    
    // Loaded entry count for each dictionary type
    private Map<String, Integer> typeLoadedCount = new HashMap<>();
    
    // Version hash for each dictionary type
    private Map<String, String> typeVersionHash = new HashMap<>();
    
    /**
     * Main method, program entry point
     */
    public static void main(String[] args) {
        TriePrecompiler precompiler = new TriePrecompiler();
        
        // Validate arguments
        if (args.length < 1) {
            System.out.println("Usage: java TriePrecompiler <output_directory>");
            System.out.println("Example: java TriePrecompiler app/src/main/assets/dict");
            System.exit(1);
        }
        
        String outputDir = args[0];
        
        // Build Trie tree
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
        
        // Set tree as loaded
        root.setIsWord(false);
        
        System.out.println("Total loaded entries: " + totalWords);
        
        // Save precompiled tree
        boolean result = precompiler.savePrecompiledTrie(new File(outputDir), root);
        if (result) {
            System.out.println("Precompiled Trie tree saved to: " + outputDir);
        } else {
            System.out.println("Failed to save precompiled Trie tree!");
        }
    }
    
    /**
     * Build Trie tree from dictionary file
     * @param dictFileName Dictionary file name
     * @param dictType Dictionary type
     * @param root Trie tree root node
     * @return Number of loaded entries
     */
    public int buildFromDictFile(String dictFileName, String dictType, TrieNode root) {
        System.out.println("Building Trie tree from dictionary file: " + dictFileName);
        
        int loadedCount = 0;
        long startTime = System.currentTimeMillis();
        
        try {
            // Open dictionary file
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(dictFileName), "UTF-8"));
            
            String line;
            // Read each line
            while ((line = reader.readLine()) != null) {
                // Parse line
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    String word = parts[0];
                    int frequency = 0;
                    
                    try {
                        frequency = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) {
                        // Ignore frequency parsing error
                        frequency = 1;
                    }
                    
                    // Insert into Trie tree
                    insert(root, word, frequency);
                    loadedCount++;
                    
                    // Log progress every 10000 entries
                    if (loadedCount % 10000 == 0) {
                        System.out.println(String.format("Loaded %d entries, type: %s", loadedCount, dictType));
                    }
                }
            }
            
            reader.close();
            
            // Estimate memory usage
            // A character takes about 2 bytes, plus 4 bytes node overhead, plus 4 bytes for frequency and other fields
            int averageWordLength = 2; // Assume average word length is 2
            int bytesPerWord = (averageWordLength * 2) + 8; // chars + node overhead + frequency
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
        } catch (IOException e) {
            System.err.println("Failed to read dictionary file: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * Insert a word into Trie tree
     * @param root Trie tree root node
     * @param word Word
     * @param frequency Frequency
     */
    private void insert(TrieNode root, String word, int frequency) {
        TrieNode current = root;
        
        for (char c : word.toCharArray()) {
            current.putIfAbsent(c, new TrieNode());
            current = current.get(c);
        }
        
        current.setIsWord(true);
        current.setFrequency(frequency);
    }
    
    /**
     * Save precompiled Trie tree to file
     * @param outputDir Output directory
     * @param root Trie tree root node
     * @return Whether successful
     */
    public boolean savePrecompiledTrie(File outputDir, TrieNode root) {
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            System.out.println("Saving precompiled Trie tree to: " + outputDir.getAbsolutePath());
            
            // Create serializable tree - we use custom serialization method
            SerializableTrieTree serializableTree = new SerializableTrieTree(root);
            
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
            info.append("  \"exportTime\": \"" + 
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date()) + "\",\n");
            info.append("  \"dictTypes\": [\n");
            
            List<String> types = new ArrayList<>(typeMemoryUsage.keySet());
            for (int i = 0; i < types.size(); i++) {
                String type = types.get(i);
                long memory = typeMemoryUsage.get(type);
                int count = typeLoadedCount.get(type);
                String version = typeVersionHash.get(type);
                
                info.append("    {\n");
                info.append("      \"type\": \"" + type + "\",\n");
                info.append("      \"count\": " + count + ",\n");
                info.append("      \"memory\": \"" + formatFileSize(memory) + "\",\n");
                info.append("      \"version\": \"" + version + "\"\n");
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
            return true;
        } catch (Exception e) {
            System.err.println("Failed to save precompiled Trie tree: " + e.getMessage());
            e.printStackTrace();
            return false;
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
     * Trie tree node class - we need a simplified implementation
     */
    public static class TrieNode implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Map<Character, TrieNode> children = new HashMap<>();
        private boolean isWord = false;
        private int frequency = 0;
        
        public TrieNode() {}
        
        public TrieNode get(char c) {
            return children.get(c);
        }
        
        public void putIfAbsent(char c, TrieNode node) {
            children.putIfAbsent(c, node);
        }
        
        public boolean isWord() {
            return isWord;
        }
        
        public void setIsWord(boolean isWord) {
            this.isWord = isWord;
        }
        
        public int getFrequency() {
            return frequency;
        }
        
        public void setFrequency(int frequency) {
            this.frequency = frequency;
        }
        
        public Map<Character, TrieNode> getChildren() {
            return children;
        }
    }
    
    /**
     * Serializable Trie tree class
     */
    public static class SerializableTrieTree implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private TrieNode root;
        private boolean isLoaded = false;
        
        public SerializableTrieTree(TrieNode root) {
            this.root = root;
            this.isLoaded = true;
        }
        
        public TrieNode getRoot() {
            return root;
        }
        
        public boolean isLoaded() {
            return isLoaded;
        }
    }
} 