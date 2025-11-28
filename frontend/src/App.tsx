import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

export function App() {
  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4" data-testid="app">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle data-testid="welcome-title">Welcome to Aionify</CardTitle>
          <CardDescription>
            Self-hosted time tracking application
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <p className="text-muted-foreground">
            This is a Quarkus + React + shadcn-ui application built with Bun.
          </p>
          <Button data-testid="get-started-button">Get Started</Button>
        </CardContent>
      </Card>
    </div>
  )
}
