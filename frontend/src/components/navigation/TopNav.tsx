import { useNavigate, useLocation } from "react-router";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { User, LogOut, Menu, Settings } from "lucide-react";
import { useState } from "react";
import { TOKEN_KEY } from "@/lib/constants";
import { Logo } from "@/components/ui/logo";

export interface NavItem {
  label: string;
  href?: string;
  icon?: React.ReactNode;
  onClick?: () => void;
}

export interface TopNavProps {
  menuItems: NavItem[];
  userName?: string;
  greeting?: string;
}

export function TopNav({ menuItems, userName, greeting }: TopNavProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const handleLogout = async () => {
    // Call backend logout to clear remember me cookie
    try {
      await fetch("/api-ui/auth/logout", {
        method: "POST",
        credentials: "include", // Include cookies
      });
    } catch (error) {
      console.error("Logout API call failed:", error);
      // Continue with logout even if API call fails
    }

    // Clear local storage
    localStorage.removeItem(TOKEN_KEY);
    navigate("/login");
  };

  const handleProfile = () => {
    // Navigate to profile based on current portal (admin or user)
    if (location.pathname.startsWith("/admin")) {
      navigate("/admin/profile");
    } else {
      navigate("/portal/profile");
    }
  };

  const handleNavClick = (item: NavItem) => {
    if (item.onClick) {
      item.onClick();
    } else if (item.href) {
      navigate(item.href);
    }
    setMobileMenuOpen(false);
  };

  return (
    <nav className="w-full bg-card border-b border-border shadow-md" data-testid="top-nav">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          {/* Logo / Brand */}
          <div className="flex-shrink-0">
            <Logo width={120} height={29} onClick={() => navigate("/")} data-testid="nav-logo" />
          </div>

          {/* Desktop Navigation - Left menu items */}
          <div className="hidden md:flex items-center space-x-4" data-testid="nav-menu-desktop">
            {menuItems.map((item, index) => (
              <Button
                key={index}
                variant="ghost"
                className="text-foreground/80 hover:text-foreground"
                onClick={() => handleNavClick(item)}
                data-testid={`nav-item-${item.label.toLowerCase().replace(/\s+/g, "-")}`}
              >
                {item.icon}
                {item.label}
              </Button>
            ))}
          </div>

          {/* Right side - Profile dropdown */}
          <div className="flex items-center gap-2">
            {/* Mobile menu button */}
            <div className="md:hidden">
              <DropdownMenu open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="text-foreground/80 hover:text-foreground"
                    data-testid="mobile-menu-button"
                  >
                    <Menu className="h-5 w-5" />
                    <span className="sr-only">Open menu</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent
                  align="end"
                  className="w-56 rounded-sm dark bg-popover text-popover-foreground"
                  data-testid="mobile-menu-dropdown"
                >
                  <DropdownMenuLabel>{t("nav.menu")}</DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  {menuItems.map((item, index) => (
                    <DropdownMenuItem
                      key={index}
                      onClick={() => handleNavClick(item)}
                      className="cursor-pointer"
                      data-testid={`mobile-nav-item-${item.label.toLowerCase().replace(/\s+/g, "-")}`}
                    >
                      {item.icon}
                      <span className="ml-2">{item.label}</span>
                    </DropdownMenuItem>
                  ))}
                </DropdownMenuContent>
              </DropdownMenu>
            </div>

            {/* Profile dropdown */}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="rounded-full text-foreground/80 hover:text-foreground"
                  data-testid="profile-menu-button"
                >
                  <User className="h-5 w-5" />
                  <span className="sr-only">Open user menu</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent
                align="end"
                className="w-56 rounded-sm dark bg-popover text-popover-foreground"
                data-testid="profile-menu-dropdown"
              >
                {(userName || greeting) && (
                  <>
                    <DropdownMenuLabel data-testid="profile-greeting">{greeting || userName}</DropdownMenuLabel>
                    <DropdownMenuSeparator />
                  </>
                )}
                <DropdownMenuItem onClick={handleProfile} className="cursor-pointer" data-testid="profile-menu-item">
                  <User className="mr-2 h-4 w-4" />
                  <span>{t("nav.profile")}</span>
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={handleLogout}
                  className="cursor-pointer text-destructive focus:text-destructive"
                  data-testid="logout-button"
                >
                  <LogOut className="mr-2 h-4 w-4" />
                  <span>{t("nav.logout")}</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </div>
    </nav>
  );
}
