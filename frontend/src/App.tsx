import { useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AppInfo } from './api'
import AppSelector from './components/AppSelector'
import AppDetail from './pages/AppDetail'

function App() {
  const [selectedApp, setSelectedApp] = useState<AppInfo | null>(null)

  return (
    <BrowserRouter>
      <div className="page" style={{ padding: 0 }}>
        <header style={{
          padding: '12px 24px',
          background: '#0d1b2a',
          borderBottom: '1px solid #1b3a5c',
          display: 'flex',
          alignItems: 'center',
          gap: 16
        }}>
          <h1 style={{ fontSize: 18, fontWeight: 600, color: '#fff' }}>App CI/CD</h1>
          <AppSelector
            onSelect={app => setSelectedApp(app)}
            onCreateNew={() => setSelectedApp(null)}
          />
        </header>
        <div style={{ flex: 1 }}>
          <Routes>
            <Route
              path="/"
              element={
                selectedApp ? (
                  <Navigate to={`/app/${selectedApp.id}`} />
                ) : (
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 'calc(100vh - 50px)', color: '#999' }}>
                    请从上方选择或创建一个 App
                  </div>
                )
              }
            />
            <Route path="/app/:id" element={<AppDetail />} />
            <Route path="/app/:id/flows" element={<AppDetail />} />
          </Routes>
        </div>
      </div>
    </BrowserRouter>
  )
}

export default App