console.log("[seckill-system] static assets loaded by nginx");
console.log("[seckill-system] /api requests will be proxied to user-service upstream");

(function renderBuildTime() {
  var el = document.getElementById("buildTime");
  if (!el) {
    return;
  }
  el.textContent = "页面加载时间: " + new Date().toLocaleString("zh-CN");
})();
