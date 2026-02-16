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

  // 1. 加载食材列表 (修复了这里)
  loadFoodList();

  // 2. 检查配置
  checkConfigStatus();

  // 绑定搜索
  const searchInput = document.getElementById('foodSearch');
  if (searchInput) {
    searchInput.addEventListener('input', (e) => filterFoods(e.target.value));
  }
});

// === 业务逻辑 ===

async function loadFoodList() {
  const container = document.getElementById('foodList');

  try {
    const res = await fetch('/api/food/list');
    // 增加网络层面的错误处理
    if (!res.ok) {
      throw new Error(`HTTP Error: ${res.status}`);
    }

    const result = await res.json();

    if (result.code === 200) {
      // 如果数据为空，显示空状态
      if (!result.data || result.data.length === 0) {
        container.innerHTML = `
                    <div class="text-center py-4 text-muted">
                        <i class="bi bi-box2 display-6 opacity-50"></i>
                        <p class="mt-2 small">冰箱空空如也<br>去<a href="/food/add-page">录入食材</a>吧</p>
                    </div>
                `;
      } else {
        renderFoods(result.data);
      }
    } else {
      // 处理业务错误 (如未登录)
      console.error("加载食材失败:", result);
      container.innerHTML = `
                <div class="text-center py-4 text-danger">
                    <i class="bi bi-exclamation-circle mb-2"></i>
                    <p class="small">${result.message || '加载失败'}</p>
                </div>
            `;
    }
  } catch (e) {
    console.error("请求异常:", e);
    container.innerHTML = `
            <div class="text-center py-4 text-danger">
                <i class="bi bi-wifi-off mb-2"></i>
                <p class="small">网络请求错误</p>
            </div>
        `;
  }
}

function renderFoods(foods) {
  const container = document.getElementById('foodList');
  container.innerHTML = '';

  foods.forEach(food => {
    const item = document.createElement('div');
    // 样式适配 Glass UI
    item.className = 'list-group-item bg-transparent border-0 border-bottom d-flex justify-content-between align-items-center py-3 px-2';
    item.innerHTML = `
            <div class="form-check m-0">
                <input class="form-check-input cursor-pointer" type="checkbox" value="${food.id}" 
                       id="check-${food.id}"
                       onchange="toggleFood(this, '${food.name}')">
                <label class="form-check-label cursor-pointer ms-2 fw-medium text-dark" for="check-${food.id}">
                    ${food.name}
                </label>
            </div>
            <span class="badge bg-white text-dark shadow-sm border rounded-pill px-3">
                ${food.quantity} ${food.unit}
            </span>
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

// === 下面保持原有的 AI 和配置逻辑不变 ===

async function checkConfigStatus() {
  try {
    const res = await fetch('/api/ai/my-config');
    if(res.ok) {
      const data = await res.json();
      if (data.code === 200) {
        if (!data.data) {
          updateDefaultConfig();
          setTimeout(() => configModal.show(), 800);
        } else {
          fillConfigForm(data.data);
        }
      }
    }
  } catch (e) {
    console.warn("AI 配置检查跳过", e);
  }
}

function fillConfigForm(config) {
  document.getElementById('config-key').value = config.apiKey || '';
  document.getElementById('config-url').value = config.baseUrl || '';
  document.getElementById('config-model').value = config.model || '';

  // 反推厂商
  const url = (config.baseUrl || '').toLowerCase();
  const select = document.getElementById('config-engine');

  if(url.includes('deepseek')) select.value = 'DeepSeek';
  else if(url.includes('aliyun') || url.includes('dashscope')) select.value = 'Qwen';
  else if(url.includes('openai')) select.value = 'OpenAI';
  else select.value = 'Custom';
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
  configModal.show();
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

  const btn = document.querySelector('#configForm button');
  const originalText = btn.innerHTML;
  btn.innerHTML = '保存中...';
  btn.disabled = true;

  try {
    const res = await fetch('/api/ai/config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config)
    });
    const data = await res.json();

    if (data.code === 200) {
      configModal.hide();
      // 简单的 Toast 提示
      alert("配置已保存！");
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

async function generateRecipe() {
  if (selectedFoodIds.size === 0) {
    alert("请先在左侧列表选择至少一种食材！");
    return;
  }

  const chatBox = document.getElementById('chatHistory');
  // 添加 loading 气泡
  const loadingId = 'loading-' + Date.now();
  const loadingHtml = `
        <div id="${loadingId}" class="d-flex mb-3 justify-content-start animate-enter">
            <div class="p-3 rounded-3 shadow-sm bg-white border">
                <div class="spinner-grow spinner-grow-sm text-primary" role="status"></div>
                <span class="ms-2 small text-muted">AI 正在根据您的食材构思食谱...</span>
            </div>
        </div>
    `;

  // 如果是第一次对话，清空初始提示
  if (chatBox.querySelector('.text-center')) chatBox.innerHTML = '';
  chatBox.insertAdjacentHTML('beforeend', loadingHtml);
  chatBox.scrollTop = chatBox.scrollHeight;

  try {
    const res = await fetch('/api/ai/generate-recipe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(Array.from(selectedFoodIds))
    });
    const data = await res.json();

    // 移除 loading
    const loadingEl = document.getElementById(loadingId);
    if(loadingEl) loadingEl.remove();

    if (data.code === 200) {
      appendMessage('assistant', data.data.content);
      document.getElementById('inputArea').style.display = 'block';
    } else if (data.message === "AI_CONFIG_MISSING") {
      alert("请先配置 AI 模型信息！");
      configModal.show();
    } else {
      appendMessage('assistant', `⚠️ 抱歉，生成失败：${data.message}`);
    }
  } catch (e) {
    document.getElementById(loadingId)?.remove();
    appendMessage('assistant', `❌ 网络请求出错：${e.message}`);
  }
}

async function sendMessage() {
  const input = document.getElementById('userMessage');
  const msg = input.value.trim();
  if (!msg) return;

  appendMessage('user', msg);
  input.value = '';

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
      appendMessage('assistant', `⚠️ 错误: ${data.message}`);
    }
  } catch (e) {
    appendMessage('assistant', "❌ 网络连接失败");
  }
}

function appendMessage(role, text) {
  const box = document.getElementById('chatHistory');
  const div = document.createElement('div');
  div.className = `d-flex mb-3 animate-enter ${role === 'user' ? 'justify-content-end' : 'justify-content-start'}`;

  const bubble = document.createElement('div');
  // AI 气泡白色，用户气泡使用渐变色
  bubble.className = `p-3 rounded-3 shadow-sm ${role === 'user' ? 'bg-gradient-blue text-white' : 'bg-white border'}`;
  bubble.style.maxWidth = "85%";

  bubble.innerHTML = role === 'user' ? text : marked.parse(text);

  div.appendChild(bubble);
  box.appendChild(div);
  box.scrollTop = box.scrollHeight;
}

async function clearHistory() {
  if(!confirm('确定要清除当前的对话记忆吗？')) return;
  await fetch('/api/ai/clear-history', { method: 'POST' });
  document.getElementById('chatHistory').innerHTML = `
        <div class="text-center text-muted mt-5 opacity-75">
            <i class="bi bi-stars fs-1 d-block mb-3 text-warning"></i>
            <p>记忆已清除，准备好开启新的美味探索了！</p>
        </div>
    `;
  document.getElementById('inputArea').style.display = 'none';
}

function filterFoods(keyword) {
  const items = document.querySelectorAll('#foodList .list-group-item');
  items.forEach(item => {
    const text = item.innerText.toLowerCase();
    item.style.display = text.includes(keyword.toLowerCase()) ? 'flex' : 'none'; // 注意这里改为 flex
  });
}