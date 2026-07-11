<template>
  <section class="auth-state">
    <p class="eyebrow">SIGN IN REQUIRED</p>
    <h1>这里收纳的是你的角色轨迹。</h1>
    <p>请先在已有的登录入口完成认证，并将 JWT 保存到浏览器的 <code>localStorage.access_token</code>，再返回查看个人角色与评测。</p>
  </section>
</template>

<style scoped>
.auth-state { display: grid; min-height: 390px; place-content: center; justify-items: center; padding: 35px; border: 1px dashed #d9d2c9; border-radius: 10px; text-align: center; }.eyebrow { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.auth-state h1 { margin: 8px 0; font-family: "Noto Serif SC", serif; font-size: 28px; }.auth-state p:last-child { max-width: 520px; color: #758084; font-size: 13px; line-height: 1.9; }.auth-state code { padding: 2px 5px; border-radius: 3px; color: #a55d43; background: #f4e9e2; font-family: "DM Mono", monospace; font-size: 11px; }
</style>
