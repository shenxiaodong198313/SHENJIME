---
license: gemma
base_model: google/Gemma-3-1B-IT
pipeline_tag: text-generation
tags:
- chat
extra_gated_heading: Access Gemma3-1B-IT on Hugging Face
extra_gated_prompt: >-
  To access Gemma3-1B-IT on Hugging Face, you are required to review and agree
  to the gemma license. To do this, please ensure you are logged in to
  Hugging Face and click below. Requests are processed immediately.
extra_gated_button_content: Acknowledge licensed
---

# litert-community/Gemma3-1B-IT

This model provides a few variants of
[google/Gemma-3-1B-IT](https://huggingface.co/google/Gemma-3-1B-IT) that are ready for
deployment on Android using the
[LiteRT (fka TFLite) stack](https://ai.google.dev/edge/litert) and
[MediaPipe LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference).

## Use the models

### Colab

*Disclaimer: The target deployment surface for the LiteRT models is
Android/iOS/Web and the stack has been optimized for performance on these
targets. Trying out the system in Colab is an easier way to familiarize yourself
with the LiteRT stack, with the caveat that the performance (memory and latency)
on Colab could be much worse than on a local device.*

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.sandbox.google.com/github/google-ai-edge/mediapipe-samples/blob/main/codelabs/litert_inference/gemma3_1b_tflite.ipynb)

### Customize

Fine tune Gemma 3 1B and deploy with either LiteRT or Mediapipe LLM Inference API:

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/#fileId=https://github.com/google-ai-edge/mediapipe-samples/blob/main/codelabs/litert_inference/Gemma3_1b_fine_tune.ipynb)

### Android

*   Download and install
    [the apk](https://github.com/google-ai-edge/gallery/releases/latest/download/ai-edge-gallery.apk).
*   Follow the instructions in the app.

To build the demo app from source, please follow the [instructions](https://github.com/google-ai-edge/gallery/blob/main/README.md)
from the GitHub repository.

### iOS

*   Clone the [MediaPipe samples](https://github.com/google-ai-edge/mediapipe-samples)
    repository and follow the [instructions](https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/llm_inference/ios/README.md)
    to build the LLM Inference iOS Sample App using XCode.
*   Run the app via the iOS simulator or deploy to an iOS device.

## Performance

### Android

Note that all benchmark stats are from a Samsung S24 Ultra and multiple prefill signatures enabled.

<table border="1">
  <tr>
   <th style="text-align: left">Backend</th>
   <th style="text-align: left">Quantization scheme</th>
   <th style="text-align: left">Context length</th>
   <th style="text-align: left">Prefill (tokens/sec)</th>
   <th style="text-align: left">Decode (tokens/sec)</th>
   <th style="text-align: left">Time-to-first-token (sec)</th>
   <th style="text-align: left">CPU Memory (RSS in MB)</th>
   <th style="text-align: left">GPU Memory (RSS in MB)</th>
   <th style="text-align: left">Model size (MB)</th>
   <th></th>
  </tr>
  <tr>
<td rowspan="8"><p style="text-align: left">CPU</p></td>
<td><p style="text-align: left">fp32 (baseline)</p></td>
<td><p style="text-align: right">1280</p></td>
<td><p style="text-align: right">49 tk/s</p></td>
<td><p style="text-align: right">10 tk/s</p></td>
<td><p style="text-align: right">5.59 s</p></td>
<td><p style="text-align: right">4,123 MB</p></td>
<td><p style="text-align: right"></p></td>
<td><p style="text-align: right">3,824 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_f32_ekv1280.task">&#128279;</a></p></td>
</tr>
<tr>
<td rowspan="2"><p style="text-align: left">dynamic_int4 (block size 128)</p></td>
<td><p style="text-align: right">1280</p></td>
<td><p style="text-align: right">138 tk/s</p></td>
<td><p style="text-align: right">50 tk/s</p></td>
<td><p style="text-align: right">2.33 s</p></td>
<td><p style="text-align: right">982 MB</p></td>
<td><p style="text-align: right"></p></td>
<td><p style="text-align: right">657 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_block128_ekv1280.task">&#128279;</a></p></td>
</tr>
<tr>
<td><p style="text-align: right">4096</p></td>
<td><p style="text-align: right">87 tk/s</p></td>
<td><p style="text-align: right">37 tk/s</p></td>
<td><p style="text-align: right">3.40 s</p></td>
<td><p style="text-align: right">1,145 MB</p></td>
<td><p style="text-align: right"></p></td>
<td><p style="text-align: right">657 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_block128_ekv4096.task">&#128279;</a></p></td>
</tr>
<tr>
<td rowspan="2"><p style="text-align: left">dynamic_int4 (block size 32)</p></td>
<td><p style="text-align: right">1280</p></td>
<td><p style="text-align: right">107 tk/s</p></td>
<td><p style="text-align: right">48 tk/s</p></td>
<td><p style="text-align: right">3.49 s</p></td>
<td><p style="text-align: right">1,045 MB</p></td>
<td><p style="text-align: right"></p></td>
<td><p style="text-align: right">688 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_block32_ekv1280.task">&#128279;</a></p></td>
</tr>
<tr>
<td><p style="text-align: right">4096</p></td>
<td><p style="text-align: right">79 tk/s</p></td>
<td><p style="text-align: right">36 tk/s</p></td>
<td><p style="text-align: right">4.40 s</p></td>
<td><p style="text-align: right">1,210 MB</p></td>
<td><p style="text-align: right"></p></td>
<td><p style="text-align: right">688 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_block32_ekv4096.task">&#128279;</a></p></td>
</tr>
<tr>
<td><p style="text-align: left">dynamic_int4 QAT</p></td>
<td><p style="text-align: right">2048</p></td>
<td><p style="text-align: right">322 tk/s</p></td>
<td><p style="text-align: right">47 tk/s</p></td>
<td><p style="text-align: right">3.10 s</p></td>
<td><p style="text-align: right">1,138 MB</p></td>
<td><p style="text-align: right"></p></td>
<td><p style="text-align: right">529 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task">&#128279;</a></p></td>
</tr>
<tr>
<td rowspan="2"><p style="text-align: left">dynamic_int8</p></td>
<td><p style="text-align: right">1280</p></td>
<td><p style="text-align: right">177 tk/s</p></td>
<td><p style="text-align: right">33 tk/s</p></td>
<td><p style="text-align: right">1.69 s</p></td>
<td><p style="text-align: right">1,341 MB</p></td>
<td><p style="text-align: right"></p></td>
<td><p style="text-align: right">1,005 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv1280.task">&#128279;</a></p></td>
</tr>
<tr>
<td><p style="text-align: right">4096</p></td>
<td><p style="text-align: right">123 tk/s</p></td>
<td><p style="text-align: right">29 tk/s</p></td>
<td><p style="text-align: right">2.34 s</p></td>
<td><p style="text-align: right">1,504 MB</p></td>
<td><p style="text-align: right"></p></td>
<td><p style="text-align: right">1,005 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv4096.task">&#128279;</a></p></td>
</tr>
<tr>
<td rowspan="3"><p style="text-align: left">GPU</p></td>
<td><p style="text-align: left">dynamic_int4 QAT</p></td>
<td><p style="text-align: right">2048</p></td>
<td><p style="text-align: right">2585 tk/s</p></td>
<td><p style="text-align: right">56 tk/s</p></td>
<td><p style="text-align: right">4.50 s</p></td>
<td><p style="text-align: right">1,205 MB</p></td>
<td><p style="text-align: right"></p></td>
<td><p style="text-align: right">529 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task">&#128279;</a></p></td>
</tr>
<tr>
<td rowspan="2"><p style="text-align: left">dynamic_int8</p></td>
<td><p style="text-align: right">1280</p></td>
<td><p style="text-align: right">1191 tk/s</p></td>
<td><p style="text-align: right">24 tk/s</p></td>
<td><p style="text-align: right">4.68 s</p></td>
<td><p style="text-align: right">2,164 MB</p></td>
<td><p style="text-align: right">1,059 MB</p></td>
<td><p style="text-align: right">1,005 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv1280.task">&#128279;</a></p></td>
</tr>
<tr>
<td><p style="text-align: right">4096</p></td>
<td><p style="text-align: right">814 tk/s</p></td>
<td><p style="text-align: right">24 tk/s</p></td>
<td><p style="text-align: right">4.99 s</p></td>
<td><p style="text-align: right">2,167 MB</p></td>
<td><p style="text-align: right">1,181 MB</p></td>
<td><p style="text-align: right">1,005 MB</p></td>
<td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv4096.task">&#128279;</a></p></td>
</tr>

</table>

*   For the list of supported quantization schemes see [supported-schemes](https://github.com/google-ai-edge/ai-edge-torch/tree/main/ai_edge_torch/generative/quantize#supported-schemes).
    For these models, we are using prefill signature lengths of 32, 128, 512 and 1280.
*   Model Size: measured by the size of the .tflite flatbuffer (serialization
    format for LiteRT models)
*   Memory: indicator of peak RAM usage
*   The inference on CPU is accelerated via the LiteRT
    [XNNPACK](https://github.com/google/XNNPACK) delegate with 4 threads
*   Benchmark is run with cache enabled and initialized. During the first run,
    the time to first token may differ.


### Web
Note that all benchmark stats are from a MacBook Pro 2024 (Apple M4 Max chip) running with 1280 KV cache size, 1024 tokens prefill, 256 tokens decode.

<table border="1">
  <tr>
   <th style="text-align: left">Backend</th>
   <th style="text-align: left">Quantization scheme</th>
   <th style="text-align: left">Precision</th>
   <th style="text-align: left">Prefill (tokens/sec)</th>
   <th style="text-align: left">Decode (tokens/sec)</th>
   <th style="text-align: left">Time-to-first-token (sec)</th>
   <th style="text-align: left">CPU Memory</th>
   <th style="text-align: left">GPU Memory</th>
   <th style="text-align: left">Model size (MB)</th>
   <th></th>
  </tr>
  <tr>
  <td rowspan="5"><p style="text-align: left">GPU</p></td>
  <td rowspan="2"><p style="text-align: left">dynamic_int4</p></td>
  <td><p style="text-align: left">F16</p></td>
  <td><p style="text-align: right">4339 tk/s</p></td>
  <td><p style="text-align: right">133 tk/s</p></td>
  <td><p style="text-align: right">0.51 s</p></td>
  <td><p style="text-align: right">460 MB</p></td>
  <td><p style="text-align: right">1,331 MB</p></td>
  <td><p style="text-align: right">700 MB</p></td>
  <td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4-web.task">&#128279;</a></p></td>
  </tr>
  <tr>
  <td><p style="text-align: left">F32</p></td>
  <td><p style="text-align: right">2837 tk/s</p></td>
  <td><p style="text-align: right">134 tk/s</p></td>
  <td><p style="text-align: right">0.49 s</p></td>
  <td><p style="text-align: right">481 MB</p></td>
  <td><p style="text-align: right">1,331 MB</p></td>
  <td><p style="text-align: right">700 MB</p></td>
  <td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4-web.task">&#128279;</a></p></td>
  </tr>
  <tr>
  <td><p style="text-align: left">dynamic_int4 QAT</p></td>
  <td><p style="text-align: left">F16</p></td>
  <td><p style="text-align: right">1702 tk/s</p></td>
  <td><p style="text-align: right">77 tk/s</p></td>
  <td><p style="text-align: right"></p></td>
  <td><p style="text-align: right"></p></td>
  <td><p style="text-align: right"></p></td>
  <td><p style="text-align: right">529 MB</p></td>
  <td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task">&#128279;</a></p></td>
  </tr>
  <tr>
  <td rowspan="2"><p style="text-align: left">dynamic_int8</p></td>
  <td><p style="text-align: left">F16</p></td>
  <td><p style="text-align: right">4321 tk/s</p></td>
  <td><p style="text-align: right">126 tk/s</p></td>
  <td><p style="text-align: right">0.6 s</p></td>
  <td><p style="text-align: right">471 MB</p></td>
  <td><p style="text-align: right">1,740 MB</p></td>
  <td><p style="text-align: right">1,011 MB</p></td>
  <td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int8-web.task">&#128279;</a></p></td>
  </tr>
  <tr>
  <td><p style="text-align: left">F32</p></td>
  <td><p style="text-align: right">2805 tk/s</p></td>
  <td><p style="text-align: right">129 tk/s</p></td>
  <td><p style="text-align: right">0.58 s</p></td>
  <td><p style="text-align: right">474 MB</p></td>
  <td><p style="text-align: right">1,740 MB</p></td>
  <td><p style="text-align: right">1,011 MB</p></td>
  <td><p style="text-align: left"><a style="text-decoration: none" href="https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int8-web.task">&#128279;</a></p></td>
  </tr>
</table>

*   Model size: measured by the size of the .tflite flatbuffer (serialization format for LiteRT models)
*   dynamic_int4: quantized model with int4 weights and float activations.
*   dynamic_int8: quantized model with int8 weights and float activations.
