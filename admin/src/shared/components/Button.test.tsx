import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { Button } from "./Button";

test("renders children correctly", () => {
  render(<Button>Click Me</Button>);
  expect(screen.getByRole("button", { name: "Click Me" })).toBeInTheDocument();
});

test("applies variant and custom classes", () => {
  render(<Button variant="primary" className="custom-class">Save</Button>);
  const button = screen.getByRole("button", { name: "Save" });
  expect(button).toHaveClass("btn", "btn-primary", "custom-class");
});

test("handles disabled and isLoading states", () => {
  const { rerender } = render(<Button disabled>Disabled</Button>);
  expect(screen.getByRole("button", { name: "Disabled" })).toBeDisabled();

  rerender(<Button isLoading>Submit</Button>);
  const loadingBtn = screen.getByRole("button");
  expect(loadingBtn).toBeDisabled();
  expect(loadingBtn).toHaveTextContent("加载中...");
});

test("triggers onClick", async () => {
  const user = userEvent.setup();
  const handleClick = vi.fn();
  render(<Button onClick={handleClick}>Click</Button>);
  await user.click(screen.getByRole("button", { name: "Click" }));
  expect(handleClick).toHaveBeenCalledTimes(1);
});
