import React, { useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AppInfo } from './api'
import AppSelector from './components/AppSelector'
import AppDetail from './pages/AppDetail'
import AppConfig from './pages/AppConfig'
import BuildRecords from './pages/BuildRecords'
import BuildRecordDetail from './pages/BuildRecordDetail'
import WorkflowList from './pages/WorkflowList'
import WorkflowCreate from './pages/WorkflowCreate'

function App() {
  const [selectedApp, setSelectedApp] = useState<AppInfo | null>(null)

  return (
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <div className="page" style={{ padding: 0 }}>
        <header style={{
          padding: '12px 24px',
          background: '#0d1b2a',
          borderBottom: '1px solid #1b3a5c',
          display: 'flex',
          alignItems: 'center',
          gap: 16
        }}>
          <img src="/favicon.svg" alt="logo" style={{ width: 24, height: 24 }} />
          <h1 style={{ fontSize: 18, fontWeight: 600, color: '#fff' }}>App CI/CD</h1>
          <AppSelector
            onSelect={app => setSelectedApp(app)}
          />
        </header>
        <div style={{ flex: 1 }}>
          <Routes>
            <Route
              path="/"
              element={
                selectedApp ? (
                  <Navigate to={`/app/${selectedApp.id}/config`} />
                ) : (
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 'calc(100vh - 50px)', color: '#999' }}>
                    请从上方选择或创建一个 App
                  </div>
                )
              }
            />
            <Route path="/app/:id" element={<Navigate to="config" />} />
            <Route path="/app/:id/config" element={<AppDetail><AppConfig /></AppDetail>} />
            <Route path="/app/:id/builds" element={<AppDetail>{React.createElement(BuildRecords, { appId: 0 })}</AppDetail>} />
            <Route path="/app/:id/builds/:workflowRunId" element={<AppDetail>{React.createElement(BuildRecordDetail, { appId: 0 })}</AppDetail>} />
            <Route path="/app/:id/workflows" element={<AppDetail><WorkflowList /></AppDetail>} />
            <Route path="/app/:id/workflow/create" element={<WorkflowCreate />} />
          </Routes>
        </div>
      </div>
    </BrowserRouter>
  )
}

export default App