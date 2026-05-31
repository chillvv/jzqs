import fs from 'fs';
import path from 'path';

const filePath = path.resolve('d:/Code/jzqs/admin/src/modules/dispatch/DispatchAreasPage.tsx');
let content = fs.readFileSync(filePath, 'utf-8');

// 1. Update AreaGroup type
content = content.replace(
  /type AreaGroup = \{/,
  'type AreaGroup = {\n  keywords: string | null;'
);

// 2. Add keywords to map.set
content = content.replace(
  /map\.set\(areaCode, \{ areaCode, riders: \[\], activeOrders: \[\], blockedOrders: \[\] \}\);/,
  'map.set(areaCode, { areaCode, keywords: "", riders: [], activeOrders: [], blockedOrders: [] });'
);

// 3. Set keywords from binding
content = content.replace(
  /const group = ensureGroup\(binding\.areaCode\);/,
  'const group = ensureGroup(binding.areaCode);\n      group.keywords = binding.keywords;'
);

// 4. Add keywords state
content = content.replace(
  /const \[renamingArea, setRenamingArea\] = useState<string \| null>\(null\);/,
  'const [renamingArea, setRenamingArea] = useState<string | null>(null);\n  const [editingKeywordsArea, setEditingKeywordsArea] = useState<string | null>(null);\n  const [keywordsValue, setKeywordsValue] = useState("");'
);

// 5. Add keywords edit functions
const renameFunctions = `  function startRename(areaCode: string) {
    setRenamingArea(areaCode);
    setRenameValue(areaCode);
  }`;
const keywordsFunctions = `  function startRename(areaCode: string) {
    setRenamingArea(areaCode);
    setRenameValue(areaCode);
  }

  function startEditKeywords(areaCode: string, currentKeywords: string | null) {
    setEditingKeywordsArea(areaCode);
    setKeywordsValue(currentKeywords || "");
  }

  async function handleEditKeywords() {
    if (!editingKeywordsArea) return;
    setSavingArea(editingKeywordsArea);
    try {
      const binding = bindings.find(b => b.areaCode === editingKeywordsArea);
      await updateDispatchAreaBinding(editingKeywordsArea, {
        keywords: keywordsValue.trim(),
        defaultRiderId: binding?.defaultRiderId || null,
        backupRiderId: binding?.backupRiderId || null,
        updatedBy: "老板"
      });
      setEditingKeywordsArea(null);
      await reload();
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setSavingArea(null);
    }
  }`;
content = content.replace(renameFunctions, keywordsFunctions);

// 6. Render keywords and edit button
const headerReplace = `              <div className="dispatch-card__header">
                <div>
                  <div className="dispatch-card__title">{group.areaCode}</div>
                  <div className="dispatch-card__subtitle">{group.riders.length} 名骑手 · 配送中 {group.activeOrders.length} 单</div>
                </div>`;
const newHeader = `              <div className="dispatch-card__header">
                <div>
                  <div className="dispatch-card__title">{group.areaCode}</div>
                  <div className="dispatch-card__subtitle" style={{ display: 'flex', alignItems: 'center' }}>
                    匹配规则: {group.keywords || "无"}
                    <button type="button" className="btn btn-outline btn-compact" style={{ marginLeft: "8px", border: "none", padding: "2px 4px", minWidth: 0 }} onClick={() => startEditKeywords(group.areaCode, group.keywords)}>
                      <Pencil size={12} />
                    </button>
                  </div>
                  <div className="dispatch-card__subtitle">{group.riders.length} 名骑手 · 配送中 {group.activeOrders.length} 单</div>
                </div>`;
content = content.replace(headerReplace, newHeader);

// 7. Add Keywords Modal
const renameModal = `      <AdminDialog
        open={Boolean(renamingArea)}`;
const keywordsModal = `      <AdminDialog
        open={Boolean(editingKeywordsArea)}
        title="编辑区域匹配规则"
        description="设置该区域的地址匹配关键词（多个词用英文逗号分隔）"
        onClose={() => setEditingKeywordsArea(null)}
        footer={
          <>
            <button className="btn btn-outline" onClick={() => setEditingKeywordsArea(null)}>取消</button>
            <button className="btn btn-primary" disabled={savingArea === editingKeywordsArea} onClick={() => handleEditKeywords().catch(() => {})}>
              保存
            </button>
          </>
        }
      >
        <label className="admin-field">
          <span className="admin-field-label">匹配关键词</span>
          <input value={keywordsValue} onChange={(event) => setKeywordsValue(event.target.value)} placeholder="例如：高新区,科技园,创新谷" style={inputStyle} autoFocus />
        </label>
      </AdminDialog>

      <AdminDialog
        open={Boolean(renamingArea)}`;
content = content.replace(renameModal, keywordsModal);

fs.writeFileSync(filePath, content, 'utf-8');
console.log('Done');