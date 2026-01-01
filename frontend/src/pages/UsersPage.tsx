import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { PortalLayout } from "@/components/layout/PortalLayout";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ConfirmationDialog } from "@/components/ui/confirmation-dialog";
import { FormMessage } from "@/components/ui/form-message";
import { apiGet, apiRequest } from "@/lib/api";
import { MoreVertical, ChevronLeft, ChevronRight, Pencil, Trash2, Plus } from "lucide-react";

interface User {
  id: number;
  userName: string;
  greeting: string;
  isAdmin: boolean;
}

interface UsersListResponse {
  users: User[];
  total: number;
  page: number;
  size: number;
}

export function UsersPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [users, setUsers] = useState<User[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [deletePopoverOpen, setDeletePopoverOpen] = useState<number | null>(null);
  const [currentUserName, setCurrentUserName] = useState<string>("");

  useEffect(() => {
    // Get current username from localStorage
    const lastUsername = localStorage.getItem("aionify_last_username");
    if (lastUsername) {
      try {
        const parsed = JSON.parse(lastUsername);
        setCurrentUserName(parsed.userName);
      } catch {
        // Ignore parsing errors
      }
    }
  }, []);

  const loadUsers = async () => {
    setLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const data = await apiGet<UsersListResponse>(`/api-ui/admin/users?page=${page}&size=${size}`);
      setUsers(data.users);
      setTotal(data.total);
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers();
  }, [page]);

  const handleDelete = async (userId: number) => {
    try {
      await apiRequest(`/api-ui/admin/users/${userId}`, {
        method: "DELETE",
      });

      setDeletePopoverOpen(null);

      // Reload the user list first
      await loadUsers();

      // Then show success message (after reload to avoid it being cleared)
      setSuccessMessage(t("portal.admin.users.deleteSuccess"));
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred");
      setDeletePopoverOpen(null);
    }
  };

  const totalPages = Math.ceil(total / size);
  const canGoPrevious = page > 0;
  const canGoNext = page < totalPages - 1;

  return (
    <PortalLayout testId="users-page">
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground" data-testid="users-title">
              {t("portal.admin.users.title")}
            </h1>
            <p className="text-muted-foreground">{t("portal.admin.users.subtitle")}</p>
          </div>

          {error && (
            <div className="mb-4">
              <FormMessage type="error" message={error} testId="users-error" />
            </div>
          )}

          {successMessage && (
            <div className="mb-4">
              <FormMessage type="success" message={successMessage} testId="users-success" />
            </div>
          )}

          {loading ? (
            <div className="text-center py-8 text-foreground" data-testid="users-loading">
              {t("common.loading")}
            </div>
          ) : (
            <>
              <Card className="border-none shadow-md" data-testid="users-table-container">
                <CardHeader>
                  <CardTitle>{t("portal.admin.users.listTitle")}</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="mb-4 flex justify-end">
                    <Button
                      variant="ghost"
                      onClick={() => navigate("/admin/users/create")}
                      data-testid="create-user-button"
                    >
                      <Plus className="h-4 w-4" />
                      {t("portal.admin.users.createUser")}
                    </Button>
                  </div>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t("portal.admin.users.table.username")}</TableHead>
                        <TableHead>{t("portal.admin.users.table.greeting")}</TableHead>
                        <TableHead>{t("portal.admin.users.table.type")}</TableHead>
                        <TableHead className="w-[50px]">{t("portal.admin.users.table.actions")}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {users.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={4} className="text-center text-muted-foreground">
                            {t("portal.admin.users.table.noUsers")}
                          </TableCell>
                        </TableRow>
                      ) : (
                        users.map((user) => (
                          <TableRow key={user.id} data-testid={`user-row-${user.userName}`}>
                            <TableCell data-testid={`user-username-${user.userName}`}>{user.userName}</TableCell>
                            <TableCell data-testid={`user-greeting-${user.userName}`}>{user.greeting}</TableCell>
                            <TableCell data-testid={`user-type-${user.userName}`}>
                              {user.isAdmin
                                ? t("portal.admin.users.table.admin")
                                : t("portal.admin.users.table.regularUser")}
                            </TableCell>
                            <TableCell>
                              {user.userName !== currentUserName && (
                                <>
                                  <DropdownMenu>
                                    <DropdownMenuTrigger asChild>
                                      <Button variant="ghost" size="sm" data-testid={`user-actions-${user.userName}`}>
                                        <MoreVertical className="h-4 w-4" />
                                      </Button>
                                    </DropdownMenuTrigger>
                                    <DropdownMenuContent align="end" className="dark">
                                      <DropdownMenuItem
                                        data-testid={`user-edit-${user.userName}`}
                                        onClick={() => navigate(`/admin/users/${user.id}`)}
                                      >
                                        <Pencil className="h-4 w-4 mr-2" />
                                        {t("portal.admin.users.table.edit")}
                                      </DropdownMenuItem>
                                      <DropdownMenuItem
                                        data-testid={`user-delete-${user.userName}`}
                                        onClick={() => setDeletePopoverOpen(user.id)}
                                        className="text-destructive focus:text-destructive"
                                      >
                                        <Trash2 className="h-4 w-4 mr-2" />
                                        {t("portal.admin.users.table.delete")}
                                      </DropdownMenuItem>
                                    </DropdownMenuContent>
                                  </DropdownMenu>

                                  <ConfirmationDialog
                                    open={deletePopoverOpen === user.id}
                                    onOpenChange={(open) => setDeletePopoverOpen(open ? user.id : null)}
                                    title={t("portal.admin.users.deleteConfirm.title")}
                                    description={t("portal.admin.users.deleteConfirm.message", {
                                      userName: user.userName,
                                    })}
                                    confirmLabel={t("portal.admin.users.deleteConfirm.confirm")}
                                    cancelLabel={t("portal.admin.users.deleteConfirm.cancel")}
                                    onConfirm={() => handleDelete(user.id)}
                                    confirmTestId={`delete-confirm-button-${user.userName}`}
                                    cancelTestId={`delete-cancel-${user.userName}`}
                                    dialogTestId={`delete-confirm-${user.userName}`}
                                  />
                                </>
                              )}
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>

              {totalPages > 1 && (
                <div className="flex items-center justify-between mt-4" data-testid="users-pagination">
                  <div className="text-sm text-muted-foreground">
                    {t("portal.admin.users.pagination.showing", {
                      start: page * size + 1,
                      end: Math.min((page + 1) * size, total),
                      total,
                    })}
                  </div>
                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage(page - 1)}
                      disabled={!canGoPrevious}
                      data-testid="pagination-previous"
                    >
                      <ChevronLeft className="h-4 w-4" />
                      {t("portal.admin.users.pagination.previous")}
                    </Button>
                    <span className="flex items-center px-4 text-sm" data-testid="pagination-info">
                      {t("portal.admin.users.pagination.page", {
                        page: page + 1,
                        total: totalPages,
                      })}
                    </span>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage(page + 1)}
                      disabled={!canGoNext}
                      data-testid="pagination-next"
                    >
                      {t("portal.admin.users.pagination.next")}
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </PortalLayout>
  );
}
