// 全局变量
let selectedFoodIds = new Set();
let configModal = null;

// 厂商默认配置字典
const ENGINE_DEFAULTS = {
  'DeepSeek': { url: 'https://api.deepseek.com', model: 'deepseek-chat' },
  'Qwen': { url: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus' },
  'OpenAI': { url: 'https://api.openai.com/v1', model: 'gpt-3.5-turbo' },
  'Custom': { url: '', model: '' }
};

document.addEventListener('DOMContentLoaded', () => {
  configModal = new bootstrap.Modal(document.getElementById('aiConfigModal'));

  // 1. 加载食材列表
  loadFoodList();

  // 2. 检查用户是否已配置 AI (核心逻辑)
  checkConfigStatus();

  // 绑定搜索事件
  document.getElementById('foodSearch').addEventListener('input', (e) => filterFoods(e.target.value));
});

// === 配置相关逻辑 ===

async function checkConfigStatus() {
  try {
    const res = await fetch('/api/ai/my-config');
    const data = await res.json();

    if (data.code === 200) {
      if (!data.data) {
        // 未配置：填充默认值并弹窗
        updateDefaultConfig();
        // 稍微延迟弹出，体验更好
        setTimeout(() => configModal.show(), 500);
      } else {
        // 已配置：回显到表单（方便用户修改）
        fillConfigForm(data.data);
      }
    }
  } catch (e) {
    console.error("配置检查失败", e);
  }
}

function fillConfigForm(config) {
  document.getElementById('config-key').value = config.apiKey || '';
  document.getElementById('config-url').value = config.baseUrl || '';
  document.getElementById('config-model').value = config.model || '';
  // 简单反推厂商逻辑（可选）
  if(config.baseUrl && config.baseUrl.includes('deepseek')) document.getElementById('config-engine').value = 'DeepSeek';
  else if(config.baseUrl && config.baseUrl.includes('aliyun')) document.getElementById('config-engine').value = 'Qwen';
  else if(config.baseUrl && config.baseUrl.includes('openai')) document.getElementById('config-engine').value = 'OpenAI';
  else document.getElementById('config-engine').value = 'Custom';
}

function updateDefaultConfig() {
  const engine = document.getElementById('config-engine').value;
  const defaults = ENGINE_DEFAULTS[engine];
  if (defaults) {
    document.getElementById('config-url').value = defaults.url;
    document.getElementById('config-model').value = defaults.model;
  }
}

function openConfigModal() {
  checkConfigStatus().then(() => configModal.show());
}

async function saveAiConfig() {
  const config = {
    apiKey: document.getElementById('config-key').value.trim(),
    baseUrl: document.getElementById('config-url').value.trim(),
    model: document.getElementById('config-model').value.trim()
  };

  if (!config.apiKey || !config.baseUrl) {
    alert("请完整填写 API Key 和 URL");
    return;
  }

  const btn = document.querySelector('#aiConfigModal .btn-primary');
  const originalText = btn.innerHTML;
  btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> 保存中...';
  btn.disabled = true;

  try {
    const res = await fetch('/api/ai/config', { // 使用你新增的 /config 接口
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config)
    });
    const data = await res.json();

    if (data.code === 200) {
      configModal.hide();
      showToast("配置保存成功", "success");
    } else {
      alert(data.message || "保存失败");
    }
  } catch (e) {
    alert("网络错误");
  } finally {
    btn.innerHTML = originalText;
    btn.disabled = false;
  }
}

// === 业务逻辑 ===

async function loadFoodList() {
  // 模拟从后端获取 (复用之前的逻辑)
  // 这里为了演示，假设调用后端 /api/food/list
  try {
    const res = await fetch('/api/food/list');
    const result = await res.json();
    if (result.code === 200) {
      renderFoods(result.data);
    }
  } catch (e) {
    document.getElementById('foodList').innerHTML = '<div class="text-danger p-3">加载失败</div>';
  }
}

function renderFoods(foods) {
  const container = document.getElementById('foodList');
  container.innerHTML = '';

  foods.forEach(food => {
    const item = document.createElement('label');
    item.className = 'list-group-item list-group-item-action d-flex justify-content-between align-items-center cursor-pointer';
    item.innerHTML = `
            <div>
                <input class="form-check-input me-2" type="checkbox" value="${food.id}" 
                       onchange="toggleFood(this, '${food.name}')">
                <span>${food.name}</span>
            </div>
            <span class="badge bg-light text-dark rounded-pill border">${food.quantity} ${food.unit}</span>
        `;
    container.appendChild(item);
  });
}

function toggleFood(checkbox, name) {
  if (checkbox.checked) {
    selectedFoodIds.add(parseInt(checkbox.value));
  } else {
    selectedFoodIds.delete(parseInt(checkbox.value));
  }
}

async function generateRecipe() {
  if (selectedFoodIds.size === 0) {
    alert("请至少选择一种食材！");
    return;
  }

  // UI Loading 状态
  const chatBox = document.getElementById('chatHistory');
  chatBox.innerHTML = `
        <div class="text-center mt-5">
            <div class="spinner-grow text-primary" role="status"></div>
            <p class="mt-2 text-muted">AI 正在思考食谱... (可能需要10-20秒)</p>
        </div>
    `;

  try {
    const res = await fetch('/api/ai/generate-recipe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(Array.from(selectedFoodIds))
    });
    const data = await res.json();

    if (data.code === 200) {
      // 显示结果
      appendMessage('assistant', data.data.content);
      document.getElementById('inputArea').style.display = 'block';
    } else if (data.message === "AI_CONFIG_MISSING" || data.code === 500) {
      // 后端抛出异常时的处理
      chatBox.innerHTML = ''; // 清空 loading
      alert("请先配置 AI 模型信息！");
      configModal.show();
    } else {
      chatBox.innerHTML = `<div class="alert alert-danger mx-3">${data.message}</div>`;
    }
  } catch (e) {
    chatBox.innerHTML = `<div class="alert alert-danger mx-3">请求出错: ${e.message}</div>`;
  }
}

async function sendMessage() {
  const input = document.getElementById('userMessage');
  const msg = input.value.trim();
  if (!msg) return;

  appendMessage('user', msg);
  input.value = '';
  input.disabled = true;

  try {
    const res = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: msg })
    });
    const data = await res.json();

    if (data.code === 200) {
      appendMessage('assistant', data.data);
    } else {
      appendMessage('assistant', `❌ 错误: ${data.message}`);
    }
  } catch (e) {
    appendMessage('assistant', "❌ 网络错误，请重试");
  } finally {
    input.disabled = false;
    input.focus();
  }
}

function appendMessage(role, text) {
  const box = document.getElementById('chatHistory');
  // 如果是第一条消息，清空初始提示
  if (box.querySelector('.text-center')) box.innerHTML = '';

  const div = document.createElement('div');
  div.className = `d-flex mb-3 ${role === 'user' ? 'justify-content-end' : 'justify-content-start'}`;

  const bubble = document.createElement('div');
  bubble.className = `p-3 rounded-3 shadow-sm ${role === 'user' ? 'bg-primary text-white' : 'bg-white border'}`;
  bubble.style.maxWidth = "85%";

  // 使用 marked 解析 markdown，并允许换行
  bubble.innerHTML = role === 'user' ? text : marked.parse(text);

  div.appendChild(bubble);
  box.appendChild(div);
  box.scrollTop = box.scrollHeight;
}

async function clearHistory() {
  if(!confirm('确定要清除 AI 的上下文记忆吗？')) return;
  await fetch('/api/ai/clear-history', { method: 'POST' });
  document.getElementById('chatHistory').innerHTML = `
        <div class="text-center text-muted mt-5">
            <i class="bi bi-robot fs-1 d-block mb-3"></i>
            <p>记忆已清除，请重新生成食谱</p>
        </div>
    `;
  document.getElementById('inputArea').style.display = 'none';
}

function showToast(msg, type='info') {
  // 简单实现，实际可用 bootstrap toast
  alert(msg);
}

// 辅助筛选
function filterFoods(keyword) {
  const items = document.querySelectorAll('#foodList .list-group-item');
  items.forEach(item => {
    const text = item.querySelector('span').innerText.toLowerCase();
    item.style.display = text.includes(keyword.toLowerCase()) ? '' : 'none';
  });
}