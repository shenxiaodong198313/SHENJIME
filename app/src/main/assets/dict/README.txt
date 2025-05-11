预编译Trie树使用说明
=================

此目录用于存放预编译的Trie树文件，以加速应用启动时的词典加载。

如何更新预编译Trie树:

1. 在测试设备上安装应用，等待词典完全加载
2. 在词典管理页面，点击右上角菜单，选择"导出预编译树"
3. 导出成功后，文件将保存在设备的外部存储目录下的export文件夹中
4. 将以下文件复制到本目录:
   - precompiled_trie.bin
   - memory_usage.bin
   - dictionary_versions.bin
5. 重新构建应用

文件说明:
- precompiled_trie.bin: 包含预构建的Trie树结构
- memory_usage.bin: 包含各词典类型内存占用信息
- dictionary_versions.bin: 包含词典版本信息

注意事项:
- 当词典数据发生变更时，请务必更新这些文件
- 预编译Trie树可能会增加APK文件大小，但会显著提高应用启动速度
- 确保这些文件与当前词典匹配，否则可能导致应用异常 