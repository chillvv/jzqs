import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { Modal } from "./Modal";

test("renders when open is true", () => {
  render(
    <Modal open={true} title="Test Modal" onClose={() => {}}>
      <div data-testid="modal-content">Content</div>
    </Modal>
  );
  expect(screen.getByText("Test Modal")).toBeInTheDocument();
  expect(screen.getByTestId("modal-content")).toBeInTheDocument();
});

test("does not render when open is false", () => {
  render(
    <Modal open={false} title="Test Modal" onClose={() => {}}>
      <div data-testid="modal-content">Content</div>
    </Modal>
  );
  expect(screen.queryByText("Test Modal")).not.toBeInTheDocument();
});

test("calls onClose when close button or overlay is clicked", async () => {
  const user = userEvent.setup();
  const handleClose = vi.fn();
  render(
    <Modal open={true} title="Test Modal" onClose={handleClose}>
      Content
    </Modal>
  );
  
  await user.click(screen.getByRole("button", { name: "关闭弹窗" }));
  expect(handleClose).toHaveBeenCalledTimes(1);

  await user.click(screen.getByTestId("modal-overlay"));
  expect(handleClose).toHaveBeenCalledTimes(2);
});

test("does not call onClose when clicking inside the modal", async () => {
  const user = userEvent.setup();
  const handleClose = vi.fn();
  render(
    <Modal open={true} title="Test Modal" onClose={handleClose}>
      <div data-testid="inner-content">Inside</div>
    </Modal>
  );
  
  await user.click(screen.getByTestId("inner-content"));
  expect(handleClose).not.toHaveBeenCalled();
});
