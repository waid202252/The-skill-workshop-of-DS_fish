import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { setUnauthorizedHandler } from './services/http'
import { ws } from './services/ws'
import { useAuthStore } from './stores/auth'
import { useChatStore } from './stores/chat'
import './styles/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 401：清理登录态、断开 WS、回到登录页
setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  const chat = useChatStore(pinia)
  auth.clearSession()
  chat.reset()
  ws.disconnect()
  if (router.currentRoute.value.path !== '/login') {
    void router.replace({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
  }
})

app.mount('#app')
