import { render, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import { Tag } from "./Tag";

test("renders children correctly", () => {
  render(<Tag>New</Tag>);
  expect(screen.getByText("New")).toBeInTheDocument();
});

test("applies variant classes correctly", () => {
  const { rerender } = render(<Tag variant="blue">Blue Tag</Tag>);
  expect(screen.getByText("Blue Tag")).toHaveClass("tag", "tag-blue");

  rerender(<Tag variant="red">Red Tag</Tag>);
  expect(screen.getByText("Red Tag")).toHaveClass("tag", "tag-red");
});
