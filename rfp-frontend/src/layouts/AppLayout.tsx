import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Menu } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
    Sheet,
    SheetContent,
    SheetTitle,
    SheetTrigger,
} from '@/components/ui/sheet';
import { SidebarNav } from '@/components/SidebarNav';

export function AppLayout() {
    const [mobileOpen, setMobileOpen] = useState(false);

    return (
        <div className="flex h-screen overflow-hidden bg-background">
            {/* Desktop sidebar (lg+): full width */}
            <aside
                className="hidden lg:flex lg:w-60 flex-col border-r border-sidebar-border bg-sidebar shrink-0"
                aria-label="Main navigation"
            >
                <SidebarNav />
            </aside>

            {/* Tablet sidebar (sm-lg): icon-only */}
            <aside
                className="hidden sm:flex lg:hidden w-16 flex-col border-r border-sidebar-border bg-sidebar shrink-0"
                aria-label="Main navigation"
            >
                <SidebarNav collapsed />
            </aside>

            {/* Main content area */}
            <div className="flex flex-1 flex-col overflow-hidden">
                {/* Top header */}
                <header className="flex h-14 items-center gap-4 border-b border-border bg-background px-4 sm:px-6">
                    {/* Mobile hamburger */}
                    <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
                        <SheetTrigger asChild>
                            <Button
                                variant="ghost"
                                size="icon"
                                className="sm:hidden"
                                aria-label="Open navigation"
                            >
                                <Menu className="h-5 w-5" />
                            </Button>
                        </SheetTrigger>
                        <SheetContent side="left" className="w-60 p-0 bg-sidebar">
                            <SheetTitle className="sr-only">Navigation</SheetTitle>
                            <SidebarNav onNavigate={() => setMobileOpen(false)} />
                        </SheetContent>
                    </Sheet>

                    <span className="text-sm font-medium text-muted-foreground sm:hidden">
                        RFP Extractor
                    </span>
                </header>

                {/* Page content */}
                <main className="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}
