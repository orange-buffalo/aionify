import * as React from "react";
import { Loader2 } from "lucide-react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const loaderVariants = cva("animate-spin text-muted-foreground", {
  variants: {
    size: {
      sm: "h-4 w-4",
      default: "h-6 w-6",
      lg: "h-8 w-8",
    },
  },
  defaultVariants: {
    size: "default",
  },
});

export interface LoaderProps extends React.HTMLAttributes<HTMLDivElement>, VariantProps<typeof loaderVariants> {
  /**
   * Whether to center the loader in its container
   */
  center?: boolean;
  /**
   * Test ID for the loader element
   */
  testId?: string;
}

const Loader = React.forwardRef<HTMLDivElement, LoaderProps>(
  ({ className, size, center = true, testId, ...props }, ref) => {
    return (
      <div
        ref={ref}
        className={cn(center && "flex justify-center items-center", className)}
        data-testid={testId}
        {...props}
      >
        <Loader2 className={cn(loaderVariants({ size }))} />
      </div>
    );
  }
);
Loader.displayName = "Loader";

export { Loader, loaderVariants };
