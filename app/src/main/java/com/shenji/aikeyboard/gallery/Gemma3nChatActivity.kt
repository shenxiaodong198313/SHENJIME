package com.shenji.aikeyboard.gallery

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import android.app.Application
import com.shenji.aikeyboard.gallery.data.ChatMessage
import com.shenji.aikeyboard.gallery.data.ChatMessageType
import com.shenji.aikeyboard.gallery.data.ModelInitializationStatus
import com.shenji.aikeyboard.gallery.ui.chat.Gemma3nChatViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Gemma-3n多模态对话Activity
 * 完全基于官方Gallery实现
 */
class Gemma3nChatActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Gemma3nChatScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gemma3nChatScreen(
    onBackPressed: () -> Unit,
    viewModel: Gemma3nChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // 设置 Context 到 ViewModel
    LaunchedEffect(Unit) {
        viewModel.setContext(context.applicationContext)
    }
    
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var textInput by remember { mutableStateOf("") }
    var showDropdownMenu by remember { mutableStateOf(false) }
    var showModelConfigDialog by remember { mutableStateOf(false) }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                selectedImage = bitmap
                Timber.d("图片选择成功: ${bitmap.width}x${bitmap.height}")
            } catch (e: Exception) {
                Timber.e(e, "加载图片失败")
                Toast.makeText(context, "加载图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 自动滚动到底部
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Gemma-3n-it-int4",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        // 显示模型状态
                        when (uiState.modelInitializationStatus) {
                            ModelInitializationStatus.INITIALIZING -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "模型加载中...",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            ModelInitializationStatus.INITIALIZED -> {
                                Text(
                                    "模型已就绪 • CPU运行",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ModelInitializationStatus.FAILED -> {
                                Text(
                                    "模型加载失败",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            ModelInitializationStatus.NOT_INITIALIZED -> {
                                Text(
                                    "等待初始化...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 下拉菜单按钮
                    Box {
                        IconButton(onClick = { showDropdownMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                        }
                        
                        DropdownMenu(
                            expanded = showDropdownMenu,
                            onDismissRequest = { showDropdownMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("模型配置") },
                                onClick = {
                                    showDropdownMenu = false
                                    showModelConfigDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("新建对话") },
                                onClick = {
                                    showDropdownMenu = false
                                    viewModel.clearHistory()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 显示模型初始化状态面板
            if (uiState.modelInitializationStatus == ModelInitializationStatus.INITIALIZING) {
                ModelInitializationPanel(
                    progress = uiState.initializationProgress
                )
            }
            
            // 消息列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(uiState.messages) { message ->
                    ChatMessageItem(message = message)
                }
                
                // 显示加载状态
                if (uiState.isLoading) {
                    item {
                        LoadingMessageItem()
                    }
                }
            }
            
            // 输入区域 - 只有在模型初始化完成后才显示
            if (uiState.modelInitializationStatus == ModelInitializationStatus.INITIALIZED) {
                ChatInputArea(
                    textInput = textInput,
                    onTextInputChange = { textInput = it },
                    selectedImage = selectedImage,
                    onImageRemove = { selectedImage = null },
                    onImagePickerClick = { imagePickerLauncher.launch("image/*") },
                    onSendMessage = {
                        if (textInput.isNotBlank() || selectedImage != null) {
                            viewModel.sendMessage(textInput, selectedImage)
                            textInput = ""
                            selectedImage = null
                        }
                    },
                    isLoading = uiState.isLoading
                )
            }
        }
    }
    
    // 模型配置对话框
    if (showModelConfigDialog) {
        ModelConfigDialog(
            onDismiss = { showModelConfigDialog = false }
        )
    }
}

@Composable
fun ModelInitializationPanel(
    progress: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "正在初始化 Gemma-3n 模型",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = progress,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.isUser
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    message.type == ChatMessageType.ERROR -> MaterialTheme.colorScheme.errorContainer
                    isUser -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // 显示图片（如果有）
                message.image?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "消息图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    if (message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // 显示文本（如果有）
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = when {
                            message.type == ChatMessageType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            isUser -> Color.White
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingMessageItem() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI正在思考...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun ChatInputArea(
    textInput: String,
    onTextInputChange: (String) -> Unit,
    selectedImage: Bitmap?,
    onImageRemove: () -> Unit,
    onImagePickerClick: () -> Unit,
    onSendMessage: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 图片预览区域
            selectedImage?.let { bitmap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "选中的图片",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "已选择图片",
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    IconButton(
                        onClick = onImageRemove,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "移除图片",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            
            // 输入区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // 图片选择按钮
                IconButton(
                    onClick = onImagePickerClick,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "选择图片",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 文本输入框
                OutlinedTextField(
                    value = textInput,
                    onValueChange = onTextInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息...") },
                    maxLines = 4,
                    enabled = !isLoading
                )
                
                // 发送按钮
                IconButton(
                    onClick = onSendMessage,
                    enabled = !isLoading && (textInput.isNotBlank() || selectedImage != null),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "发送",
                        tint = if (!isLoading && (textInput.isNotBlank() || selectedImage != null)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ModelConfigDialog(
    onDismiss: () -> Unit
) {
    var maxTokens by remember { mutableStateOf(4096) }
    var topK by remember { mutableStateOf(64) }
    var topP by remember { mutableStateOf(0.95f) }
    var temperature by remember { mutableStateOf(1.0f) }
    var selectedAccelerator by remember { mutableStateOf("CPU") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Model configs",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Max tokens
                Column {
                    Text("Max tokens", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(maxTokens.toString(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                // TopK
                Column {
                    Text("TopK", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Slider(
                            value = topK.toFloat(),
                            onValueChange = { topK = it.toInt() },
                            valueRange = 1f..100f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            topK.toString(),
                            fontSize = 14.sp,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
                
                // TopP
                Column {
                    Text("TopP", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Slider(
                            value = topP,
                            onValueChange = { topP = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            String.format("%.2f", topP),
                            fontSize = 14.sp,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
                
                // Temperature
                Column {
                    Text("Temperature", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0f..2f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            String.format("%.2f", temperature),
                            fontSize = 14.sp,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
                
                // Choose accelerator
                Column {
                    Text("Choose accelerator", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            onClick = { selectedAccelerator = "CPU" },
                            label = { Text("CPU") },
                            selected = selectedAccelerator == "CPU",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            onClick = { selectedAccelerator = "GPU" },
                            label = { Text("GPU") },
                            selected = selectedAccelerator == "GPU"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
} 