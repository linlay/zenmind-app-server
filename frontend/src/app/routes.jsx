import { ClientsPage } from '../features/clients/ClientsPage';
import { InboxPage } from '../features/inbox/InboxPage';
import { SecurityPage } from '../features/security/SecurityPage';
import { ToolsPage } from '../features/tools/ToolsPage';
import { UsersPage } from '../features/users/UsersPage';

export const protectedRoutes = [
  { path: '/users', label: 'Users', element: <UsersPage /> },
  { path: '/clients', label: 'Clients', element: <ClientsPage /> },
  { path: '/inbox', label: 'Inbox', element: <InboxPage /> },
  { path: '/security', label: 'Security', element: <SecurityPage /> },
  { path: '/tools', label: 'Tools', element: <ToolsPage /> }
];
