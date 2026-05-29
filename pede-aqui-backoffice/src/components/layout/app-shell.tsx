"use client";

import { useEffect } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAppDispatch, useAppSelector } from "@/store/hooks";
import { setSearchQuery, toggleSidebar } from "@/store/slices/ui-slice";
import { logout as logoutAction } from "@/store/slices/auth-slice";
import { cn } from "@/lib/utils";
import {
  BarChart3, Bell, Boxes, Building2, CreditCard,
  Headphones, LayoutDashboard, LayoutGrid, Menu, Search, ShieldCheck,
  LogOut, ChevronRight, Package, Truck,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const navigation = [
  { href: "/", label: "Painel Principal", icon: LayoutDashboard, roles: [] as string[] },
  { href: "/empresa", label: "A Minha Empresa", icon: Building2, roles: ["VENDOR_ADMIN", "ADMIN"] },
  { href: "/admin", label: "Admin Central", icon: ShieldCheck, roles: ["ADMIN"] },
  { href: "/catalogo", label: "Catálogo", icon: LayoutGrid, roles: ["ADMIN", "VENDOR_ADMIN", "OPS"] },
  { href: "/vendors", label: "Vendedores", icon: Building2, roles: ["ADMIN", "VENDOR_ADMIN", "OPS"] },
  { href: "/orders", label: "Encomendas", icon: Package, roles: ["ADMIN", "VENDOR_ADMIN", "OPS", "SUPPORT"] },
  { href: "/couriers", label: "Estafetas", icon: Truck, roles: ["ADMIN", "OPS"] },
  { href: "/finance", label: "Finanças", icon: CreditCard, roles: ["ADMIN", "FINANCE"] },
  { href: "/support", label: "Apoio", icon: Headphones, roles: ["ADMIN", "SUPPORT"] },
  { href: "/marketing", label: "Marketing", icon: BarChart3, roles: ["ADMIN", "OPS"] },
  { href: "/screens", label: "Ecrãs (Stitch)", icon: Boxes, roles: [] as string[] },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const dispatch = useAppDispatch();
  const collapsed = useAppSelector((state) => state.ui.sidebarCollapsed);
  const query = useAppSelector((state) => state.ui.searchQuery);
  const user = useAppSelector((state) => state.auth.user);
  const isAuthenticated = useAppSelector((state) => state.auth.isAuthenticated);

  useEffect(() => {
    if (!isAuthenticated) {
      router.replace("/login");
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated) {
    return null;
  }

  const handleLogout = () => {
    sessionStorage.removeItem('auth_token');
    sessionStorage.removeItem('tenant_id');
    dispatch(logoutAction());
    router.push('/login');
  };

  const userRole = user?.role ?? "";
  const visibleNav = navigation.filter(
    (item) => item.roles.length === 0 || item.roles.includes(userRole),
  );

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,#ffdad2_0,transparent_32%),linear-gradient(135deg,#fff8f6_0%,#fff1ed_45%,#fff8f6_100%)] text-on-surface">
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-30 hidden border-r border-outline-variant bg-white/80 backdrop-blur-xl transition-all duration-300 lg:flex lg:flex-col",
          collapsed ? "w-20" : "w-72",
        )}
      >
        <div className="flex h-20 items-center gap-3 border-b border-outline-variant px-5">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary text-on-primary shadow-card">
            <Boxes className="h-5 w-5" />
          </div>
          {!collapsed && (
            <div>
              <p className="font-headline-sm text-lg leading-tight">Pede Aqui</p>
              <p className="text-xs font-bold uppercase tracking-wider text-on-surface-variant">Backoffice</p>
            </div>
          )}
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto p-3">
          {visibleNav.map((item) => {
            const active = pathname === item.href;
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-bold transition-all",
                  active
                    ? "bg-primary-fixed text-on-primary-fixed-variant shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container",
                )}
              >
                <Icon className="h-5 w-5 shrink-0" />
                {!collapsed && (
                  <>
                    <span className="flex-1">{item.label}</span>
                    <ChevronRight className={cn("h-4 w-4 transition-opacity", active ? "opacity-100" : "opacity-0")} />
                  </>
                )}
              </Link>
            );
          })}
        </nav>

        <div className="border-t border-outline-variant p-3">
          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-bold text-on-surface-variant transition-colors hover:bg-surface-container"
          >
            <LogOut className="h-5 w-5 shrink-0" />
            {!collapsed && <span>Terminar Sessão</span>}
          </button>
        </div>
      </aside>

      <div className={cn("transition-all duration-300", collapsed ? "lg:pl-20" : "lg:pl-72")}>
        <header className="sticky top-0 z-20 flex h-20 items-center justify-between border-b border-outline-variant bg-background/85 px-4 backdrop-blur-xl md:px-8">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={() => dispatch(toggleSidebar())} aria-label="Alternar barra lateral">
              <Menu className="h-5 w-5" />
            </Button>
            <div className="hidden w-[360px] items-center gap-2 rounded-2xl border border-outline-variant bg-white px-3 shadow-sm md:flex">
              <Search className="h-4 w-4 text-on-surface-variant" />
              <Input
                className="h-11 border-0 bg-transparent px-1 shadow-none focus-visible:ring-0"
                placeholder="Pesquisar módulos, ecrãs, relatórios..."
                value={query}
                onChange={(event) => dispatch(setSearchQuery(event.target.value))}
              />
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" aria-label="Notificações">
              <Bell className="h-5 w-5" />
            </Button>
            <div className="hidden text-right md:block">
              <p className="text-sm font-bold">{user?.name ?? "Utilizador"}</p>
              <p className="text-xs text-on-surface-variant">{user?.role ?? ""}</p>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-full bg-primary text-sm font-bold text-on-primary">
              {user?.name?.split(" ").map(n => n[0]).join("").slice(0, 2).toUpperCase() ?? "U"}
            </div>
          </div>
        </header>
        {children}
      </div>
    </div>
  );
}
