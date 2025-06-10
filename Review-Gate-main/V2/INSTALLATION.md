# Review Gate V2 - Complete Installation Guide

## Overview
Review Gate V2 is a powerful MCP (Model Context Protocol) server that adds interactive popup dialogs to Cursor with support for text input, image uploads, and speech-to-text functionality. This guide will help you install both the MCP server and Cursor extension globally so they work across all your projects.

## Prerequisites

### System Requirements
- **macOS** (tested on macOS 14+)
- **Cursor IDE** (latest version)
- **Python 3.8+** with pip
- **Node.js** (for extension building, if needed)
- **Homebrew** (for SoX installation)

### Install Dependencies
```bash
# Install SoX for speech-to-text functionality
brew install sox

# Verify SoX installation
sox --version
```

## Part 1: MCP Server Installation

### Step 1: Create Global Installation Directory
```bash
# Create a dedicated directory for Review Gate V2
mkdir -p ~/cursor-extensions/review-gate-v2
cd ~/cursor-extensions/review-gate-v2
```

### Step 2: Copy Files
Copy the following files from this `Review Gate V2` folder to your installation directory:
```bash
# Copy MCP server and requirements
cp /path/to/this/folder/review_gate_v2_mcp.py ~/cursor-extensions/review-gate-v2/
cp /path/to/this/folder/requirements_simple.txt ~/cursor-extensions/review-gate-v2/
```

### Step 3: Create Python Virtual Environment
```bash
cd ~/cursor-extensions/review-gate-v2

# Create virtual environment
python3 -m venv venv

# Activate virtual environment
source venv/bin/activate

# Install dependencies
pip install -r requirements_simple.txt
```

### Step 4: Test MCP Server
```bash
# Test the server runs without errors
python review_gate_v2_mcp.py
# Should show initialization messages, then Ctrl+C to stop
```

## Part 2: Global MCP Configuration

### Step 1: Locate Cursor MCP Config
The global Cursor MCP configuration file location:
```bash
# Global MCP config location:
~/.cursor/mcp.json

# Create the directory if it doesn't exist
mkdir -p ~/.cursor
```

### Step 2: Create or Update MCP Config
Create/edit the MCP configuration file:
```bash
# Open in your preferred editor
nano ~/.cursor/mcp.json
```

Add the Review Gate V2 server configuration:
```json
{
  "mcpServers": {
    "review-gate-v2": {
      "command": "/Users/YOUR_USERNAME/cursor-extensions/review-gate-v2/venv/bin/python",
      "args": ["/Users/YOUR_USERNAME/cursor-extensions/review-gate-v2/review_gate_v2_mcp.py"],
      "env": {
        "PYTHONPATH": "/Users/YOUR_USERNAME/cursor-extensions/review-gate-v2",
        "PYTHONUNBUFFERED": "1",
        "REVIEW_GATE_MODE": "cursor_integration"
      }
    }
  }
}
```

**Important**: Replace `YOUR_USERNAME` with your actual macOS username!

## Part 3: Cursor Extension Installation

### Step 1: Install Extension Package
1. Copy the `review-gate-v2-2.5.1.vsix` file to a permanent location:
   ```bash
   cp /path/to/this/folder/simple-extension/review-gate-v2-2.5.1.vsix ~/cursor-extensions/review-gate-v2/
   ```

2. Open Cursor IDE
3. Press `Cmd+Shift+P` to open command palette
4. Type "Extensions: Install from VSIX"
5. Select the `review-gate-v2-2.5.1.vsix` file
6. Restart Cursor when prompted

### Step 2: Verify Extension Installation
1. Open Extensions panel (`Cmd+Shift+X`)
2. Look for "Review Gate V2 ゲート" in installed extensions
3. Should show as enabled

## Part 4: Global Rule Configuration (Optional)

### Install the Review Gate V2 Rule
1. Copy the `ReviewGate.mdc` file to Cursor's rules directory:
   ```bash
   # Create rules directory if needed
   mkdir -p ~/Library/Application\ Support/Cursor/User/rules/
   
   # Copy the rule
   cp /path/to/this/folder/ReviewGate.mdc ~/Library/Application\ Support/Cursor/User/rules/
   ```

2. Alternative: Add via Cursor Settings
   - Open Cursor Settings (`Cmd+,`)
   - Go to "Rules"
   - Click "Add Rule"
   - Paste the content of `ReviewGate.mdc`

## Part 5: Testing Installation

### Test 1: MCP Server Connection
1. Open any project in Cursor
2. Check if MCP server is running:
   ```bash
   # Check for log file
   tail -f /tmp/review_gate_v2.log
   ```
3. Should see initialization messages

### Test 2: Extension Functionality
1. In Cursor, press `Cmd+Shift+R` (manual trigger)
2. Review Gate popup should appear
3. Test features:
   - Type text and send
   - Click camera icon to upload image
   - Click microphone for speech-to-text

### Test 3: MCP Tool Integration
1. Start a new chat in Cursor
2. Ask: "Use the review_gate_chat tool to get my feedback"
3. Popup should appear automatically
4. Provide feedback and verify response

## Troubleshooting

### Common Issues

#### 1. MCP Server Not Starting
```bash
# Check Python path and permissions
which python3
ls -la ~/cursor-extensions/review-gate-v2/

# Test server manually
cd ~/cursor-extensions/review-gate-v2
source venv/bin/activate
python review_gate_v2_mcp.py
```

#### 2. Extension Not Loading
- Verify extension is enabled in Extensions panel
- Restart Cursor completely
- Check browser console for errors (F12)

#### 3. Speech-to-Text Not Working
```bash
# Test SoX installation
sox -d -r 16000 -c 1 test.wav trim 0 3
ls -la test.wav
rm test.wav

# Check microphone permissions in System Preferences
```

#### 4. Popup Not Appearing
- Check MCP server logs: `tail -f /tmp/review_gate_v2.log`
- Verify trigger files: `ls -la /tmp/review_gate_*`
- Restart both Cursor and check config paths

### Debug Commands
```bash
# Monitor MCP server
tail -f /tmp/review_gate_v2.log

# Check extension logs
# Open Cursor → F12 → Console

# Monitor trigger files
watch "ls -la /tmp/review_gate_*"

# Test microphone
sox -d -r 16000 -c 1 test.wav trim 0 2 && rm test.wav
```

## File Structure
After installation, your file structure should look like:
```
~/cursor-extensions/review-gate-v2/
├── review_gate_v2_mcp.py          # MCP server
├── requirements_simple.txt         # Python dependencies
├── review-gate-v2-2.5.1.vsix      # Extension package
├── venv/                           # Python virtual environment
└── INSTALLATION.md                 # This file

~/.cursor/
└── mcp.json                        # Global MCP config

~/Library/Application Support/Cursor/User/rules/
└── ReviewGate.mdc                  # Optional: Global rule
```

## Uninstallation

To completely remove Review Gate V2:
```bash
# Remove extension via Cursor Extensions panel
# Remove installation directory
rm -rf ~/cursor-extensions/review-gate-v2

# Remove MCP config entry
# Edit ~/.cursor/mcp.json

# Remove global rule
rm ~/Library/Application\ Support/Cursor/User/rules/ReviewGate.mdc

# Clean up temp files
rm -f /tmp/review_gate_*
```

## Support

If you encounter issues:
1. Check the troubleshooting section above
2. Review the logs in `/tmp/review_gate_v2.log`
3. Verify all file paths match your system
4. Ensure all dependencies are properly installed

## Features Available After Installation

✅ **Global MCP Integration** - Works in all Cursor projects  
✅ **Text Input** - Standard feedback and sub-prompts  
✅ **Image Upload** - Screenshots, mockups, reference images  
✅ **Speech-to-Text** - Voice input with local AI transcription  
✅ **Professional UI** - Clean popup with orange glow design  
✅ **Multi-Modal Responses** - Text and images via MCP protocol  
✅ **5-Minute Timeout** - Reliable response handling  
✅ **Manual Trigger** - `Cmd+Shift+R` hotkey  
✅ **Auto Integration** - Works with global rules for automatic activation  

Your Review Gate V2 is now ready to supercharge your Cursor workflow! 🚀