import { NavLink } from 'react-router-dom';
import { Upload, Briefcase, LogOut } from 'lucide-react';
import { cn } from '@/lib/utils';

interface SidebarNavProps {
    collapsed?: boolean;
    onNavigate?: () => void;
}

const navItems = [
    { to: '/jobs', label: 'Jobs', icon: Briefcase },
    { to: '/upload', label: 'Upload', icon: Upload },
];

export function SidebarNav({ collapsed = false, onNavigate }: SidebarNavProps) {
    return (
        <div className="flex flex-col h-full">
            <div className="p-4">
                <h1 className={cn(
                    'font-bold text-foreground transition-all',
                    collapsed ? 'text-center text-sm' : 'text-lg',
                )}>
                    {collapsed ? 'RFP' : 'RFP Extractor'}
                </h1>
            </div>

            <nav className="flex-1 px-2 space-y-1" aria-label="Main navigation">
                {navItems.map((item) => (
                    <NavLink
                        key={item.to}
                        to={item.to}
                        onClick={onNavigate}
                        className={({ isActive }) => cn(
                            'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                            isActive
                                ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                                : 'text-sidebar-foreground hover:bg-sidebar-accent/50',
                            collapsed && 'justify-center px-2',
                        )}
                    >
                        <item.icon className="h-5 w-5 shrink-0" />
                        {!collapsed && <span>{item.label}</span>}
                    </NavLink>
                ))}
            </nav>

            <div className="p-2 border-t border-sidebar-border">
                <button
                    className={cn(
                        'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium w-full',
                        'text-sidebar-foreground hover:bg-sidebar-accent/50 transition-colors',
                        collapsed && 'justify-center px-2',
                    )}
                    disabled
                    title="Login available in Sprint 11"
                >
                    <LogOut className="h-5 w-5 shrink-0" />
                    {!collapsed && <span>Logout</span>}
                </button>
            </div>
        </div>
    );
}
