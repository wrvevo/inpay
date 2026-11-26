import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("E-mail ou documento").fill("admin@inpay.local");
  await page.getByLabel("Senha").fill("inpay@123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(
    page.getByRole("heading", { name: "Sua operação, em perspectiva." }),
  ).toBeVisible();
});

test("dashboard, filters, details and downloads", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Sua operação, em perspectiva." }),
  ).toBeVisible();
  await page.screenshot({
    path: "test-results/overview-desktop.png",
    fullPage: true,
  });
  await page.getByLabel("Período", { exact: true }).selectOption("7");
  await page.getByRole("button", { name: "Transações", exact: true }).click();
  await page.getByLabel("Buscar transações").fill("Marina");
  await expect(page.locator("tbody tr").first()).toContainText("Marina");
  await page.getByLabel("Status", { exact: true }).selectOption("Em análise");
  await page
    .getByRole("button", { name: /Detalhes demo_/ })
    .first()
    .click();
  await expect(page.getByRole("dialog")).toContainText("Histórico ilustrativo");
  await page.getByRole("button", { name: "Fechar", exact: true }).click();
  const downloaded = page.waitForEvent("download");
  await page.getByRole("button", { name: "Exportar CSV" }).click();
  expect((await downloaded).suggestedFilename()).toContain(".csv");
  expect(errors).toEqual([]);
});

test("demo link creation and explicit revocation", async ({ page }) => {
  await page.goto("/#links");
  await page.getByRole("button", { name: "Novo link" }).click();
  await page.getByLabel("Referência do pedido").fill("Pedido E2E");
  await page.getByLabel("Valor (R$)").fill("125,90");
  await page.getByLabel("Validade").fill("2099-12-01T12:00");
  await page
    .getByRole("button", { name: "Criar registro", exact: true })
    .click();
  const row = page.getByRole("row").filter({ hasText: "Pedido E2E" });
  await expect(row).toContainText("125,90");
  await row.getByRole("button", { name: "Revogar" }).click();
  await page.getByRole("button", { name: "Confirmar revogação" }).click();
  await expect(row).toContainText("REVOKED");
});

test("all provider forms, sandbox and production templates", async ({
  page,
}) => {
  await page.goto("/#methods");
  await expect(page.locator(".provider")).toHaveCount(8);
  for (let index = 0; index < 8; index++) {
    await page
      .locator(".provider")
      .nth(index)
      .getByRole("button", { name: "Configurar", exact: true })
      .click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(
      page.getByRole("dialog").locator("input[type=password]"),
    ).toHaveCount(0);
    await page.getByLabel("Ambiente do template").selectOption("production");
    if (index === 1)
      await expect(page.getByLabel("token-url", { exact: false })).toHaveValue(
        "",
      );
    await page.getByRole("button", { name: "Fechar", exact: true }).click();
  }
  await page
    .locator(".provider")
    .first()
    .getByRole("button", { name: "Configurar" })
    .click();
  await page
    .getByLabel("tenant-id", { exact: false })
    .fill("11111111-1111-1111-1111-111111111111");
  const downloaded = page.waitForEvent("download");
  await page.getByRole("button", { name: "Exportar template" }).click();
  expect((await downloaded).suggestedFilename()).toBe(
    "inpay-cielo-sandbox.env.example",
  );
});

test("connected mode isolates data, forwards token and maps real link contract", async ({
  page,
}) => {
  const id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
  await page.route("**/api/links", async (route) => {
    expect(route.request().headers()["authorization"]).toBe(
      "Bearer test-access-token",
    );
    expect(route.request().postDataJSON().amount).toBe(1001);
    await route.fulfill({
      json: {
        id,
        orderId: "Real test",
        amount: 1001,
        revoked: false,
        expiresAt: "2099-01-01T00:00:00Z",
      },
    });
  });
  await page.goto("/#settings");
  await page.getByLabel("Access token Cognito").fill("test-access-token");
  await page.getByRole("button", { name: "Usar modo conectado" }).click();
  await page.getByRole("button", { name: "Visão geral", exact: true }).click();
  await expect(
    page.getByText("Indicadores aguardando integração"),
  ).toBeVisible();
  await expect(page.locator(".metric")).toHaveCount(0);
  await page
    .getByRole("button", { name: "Links de pagamento", exact: true })
    .click();
  await expect(page.getByText("Nenhum link nesta sessão")).toBeVisible();
  await page.getByRole("button", { name: "Novo link" }).click();
  await page.getByLabel("Referência do pedido").fill("Real test");
  await page.getByLabel("Valor (R$)").fill("10,01");
  await page.getByLabel("Validade").fill("2099-12-01T12:00");
  await page
    .getByRole("button", { name: "Criar registro", exact: true })
    .click();
  await expect(
    page.getByRole("row").filter({ hasText: "Real test" }),
  ).toContainText("ACTIVE");
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain(
    "test-access-token",
  );
  await page.route("**/api/transactions/*", (route) =>
    route.fulfill({ status: 403, json: {} }),
  );
  await page.getByRole("button", { name: "Transações", exact: true }).click();
  await page.getByLabel("Buscar transações").fill(id);
  await page.getByRole("button", { name: "Consultar", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Sem permissão");
});

test("all pages render and mobile navigation has no horizontal overflow", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const pages = [
    "journey",
    "routing",
    "subscriptions",
    "customers",
    "recipients",
    "split",
    "finance",
    "fraud",
    "disputes",
    "developers",
    "team",
    "settings",
  ];
  for (const target of pages) {
    await page.goto(`/#${target}`);
    await expect(page.locator("main h1")).toBeVisible();
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Abrir menu" })).toBeVisible();
  await page.getByRole("button", { name: "Abrir menu" }).click();
  await page.getByLabel("Aplicação InPay").selectOption("accounts");
  await page.getByRole("button", { name: "Abrir menu" }).click();
  await page
    .getByRole("button", { name: "Meios e adquirentes (templates)", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Meios de pagamento", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: "test-results/providers-mobile.png",
    fullPage: true,
  });
  expect(errors).toEqual([]);
});

test("risk simulation and split retain the expected decision and cents", async ({
  page,
}) => {
  await page.goto("/#fraud");
  await page.getByLabel("Comprometimento conhecido").check();
  await page.getByRole("button", { name: "Avaliar risco" }).click();
  await expect(page.locator("pre")).toContainText("REJECT");
  await page.goto("/#split");
  await page.getByLabel("Valor da venda (R$)").fill("10,01");
  await expect(page.locator("dd").first()).toHaveText("R$ 1,00");
  await expect(page.locator("dd").last()).toHaveText("R$ 9,01");
});


test("product navigation isolates menus and deep links select their owner", async ({ page }) => {
  await expect(page.getByLabel("Aplicação InPay")).toHaveValue("gateway");
  await expect(page.getByRole("button", { name: "Vendedores e repasses" })).toHaveCount(0);
  await page.getByLabel("Aplicação InPay").selectOption("marketplace");
  await expect(page.getByRole("button", { name: "Vendedores e repasses" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Transações", exact: true })).toHaveCount(0);
  await page.goto("/#methods");
  await expect(page.getByLabel("Aplicação InPay")).toHaveValue("accounts");
  await expect(page.getByRole("heading", { name: "Meios de pagamento", exact: true })).toBeVisible();
});
