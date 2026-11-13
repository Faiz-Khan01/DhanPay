import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import SendMoneyPage from './pages/SendMoneyPage';
import TransactionHistoryPage from './pages/TransactionHistoryPage';
import EscrowPage from './pages/EscrowPage';
import RecurringTransfersPage from './pages/RecurringTransfersPage';
import DeveloperPortalPage from './pages/DeveloperPortalPage';
import AdminDashboardPage from './pages/AdminDashboardPage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/send-money" element={<SendMoneyPage />} />
            <Route path="/history" element={<TransactionHistoryPage />} />
            <Route path="/escrow" element={<EscrowPage />} />
            <Route path="/recurring" element={<RecurringTransfersPage />} />
            <Route path="/developer" element={<DeveloperPortalPage />} />
            <Route
              path="/admin"
              element={
                <ProtectedRoute requiredRole="ROLE_ADMIN">
                  <AdminDashboardPage />
                </ProtectedRoute>
              }
            />
          </Route>

          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
