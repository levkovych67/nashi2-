import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { importWarning } from '../selection/store'
import { StatesGallery } from '../pages/StatesGallery'
import { StudioLayout } from './StudioLayout'
import { OverviewPage } from '../pages/OverviewPage'
import { DecisionPage } from '../pages/DecisionPage'
import { VariantFullscreenPage } from '../pages/VariantFullscreenPage'
import { FlowPage } from '../pages/FlowPage'
import { HandoffPage } from '../pages/HandoffPage'
import { AuditPage } from '../pages/AuditPage'

function MissingPage() {
  return <main className="studio-page"><h1>Сторінку не знайдено</h1><Link className="studio-action" to="/">До каталогу рішень</Link></main>
}

export function StudioRouter() {
  return (
    <BrowserRouter>
      {importWarning() && <p className="import-warning" role="status">{importWarning()}</p>}
      <Routes>
        <Route path="/v/:decisionId/:variantId" element={<VariantFullscreenPage />} />
        <Route element={<StudioLayout />}>
          <Route index element={<OverviewPage />} />
          <Route path="d/:decisionId" element={<DecisionPage />} />
          <Route path="flow/:scenarioId" element={<FlowPage />} />
          <Route path="handoff" element={<HandoffPage />} />
          <Route path="states" element={<StatesGallery/>} />
          <Route path="audit" element={<AuditPage />} />
          <Route path="*" element={<MissingPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
