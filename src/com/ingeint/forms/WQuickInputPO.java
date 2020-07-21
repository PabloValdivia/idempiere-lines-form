package com.ingeint.forms;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.ListModelTable;
import org.adempiere.webui.editor.WNumberEditor;
import org.adempiere.webui.editor.WSearchEditor;
import org.adempiere.webui.editor.WTableDirEditor;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.event.WTableModelEvent;
import org.adempiere.webui.event.WTableModelListener;
import org.adempiere.webui.theme.ThemeManager;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MUOM;
import org.compiere.model.Query;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.compiere.util.Util;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Center;
import org.zkoss.zul.Div;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listhead;
import org.zkoss.zul.Listheader;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.ListitemRenderer;
import org.zkoss.zul.South;

import com.ingeint.base.CustomForm;
import com.ingeint.util.Utils;

public class WQuickInputPO extends CustomForm implements WTableModelListener {

	private static final long serialVersionUID = 1L;
	private Properties ctx = Env.getCtx();
	private WSearchEditor fProduct;
	private org.zkoss.zul.Listbox productTable;
	private ConfirmPanel confirmPanel = new ConfirmPanel(true);
	private WNumberEditor fQtyOnOK;
	private WNumberEditor fPriceActual;
	private WNumberEditor fDiscount;
	private WNumberEditor fTotalLines;
	private MOrder order;
	private int C_Product;
	private int C_Qty;
	private int C_Line;
	private Button btnDelete;
	private Vector<Vector<Object>> linesData;
	private int C_UOM;
	private int C_Price;
	private int C_Discount;
	private int C_Total;
	private WTableDirEditor fUOM;
	private int C_IsUpdate;

	@Override
	protected void initForm() {
		setWidth("100%");
		setHeight("70%");
		setClosable(false);
		setBorder("normal");

		try {
			initComponent();
			zkInit();
			loadLines();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void zkInit() {
		Borderlayout layout = new Borderlayout();
		layout.setHeight("100%");
		layout.setWidth("100%");
		this.appendChild(layout);

		Center center = new Center();
		layout.appendChild(center);

		Borderlayout cBorderLayout = new Borderlayout();
		center.appendChild(cBorderLayout);

		Center cCenter = new Center();
		cBorderLayout.appendChild(cCenter);
		cCenter.appendChild(productTable);

		South south = new South();
		layout.appendChild(south);
		south.appendChild(confirmPanel);

		confirmPanel.addActionListener(Events.ON_CLICK, this);
		fDiscount.getComponent().addEventListener(Events.ON_OK, this);
		fDiscount.getComponent().addEventListener(Events.ON_BLUR, this);
		productTable.addEventListener(Events.ON_DOUBLE_CLICK, this);
	}

	public void onEvent(Event e) throws Exception {
		if (e.getTarget().equals(confirmPanel.getButton(ConfirmPanel.A_OK))) {
			detach();
		} else if (e.getTarget() == fDiscount.getComponent().getDecimalbox()) {
			if (e.getName().equals(Events.ON_OK)) {
				saveNewLine();
				loadLines();
				fDiscount.setValue(null);
				fPriceActual.setValue(null);
				fQtyOnOK.setValue(null);
			}
			fProduct.getComponent().setFocus(true);
		} else if (e.getTarget().equals(productTable)) {
			if (e.getName().equals(Events.ON_DOUBLE_CLICK)) {
				productTableDoubleCLick();
			}
		}
	}

	private void productTableDoubleCLick() {
		final Integer index = productTable.getSelectedIndex();

		if (index < 0)
			return;

		loadLinesDoubleClick(index);

	}

	private void initComponent() throws Exception {

		int Record_ID = getGridTab().getRecord_ID();
		order = new MOrder(ctx, Record_ID, null);

		MLookup lookupProduct = MLookupFactory.get(Env.getCtx(), getWindowNo(), 0, DisplayType.Search,
				Env.getLanguage(ctx), "M_Product_ID", 0, false,
				"M_Product.AD_Client_ID = @#AD_Client_ID@ and M_Product.IsActive='Y'");

		MLookup lookupUOM = MLookupFactory.get(Env.getCtx(), getWindowNo(), 0, DisplayType.TableDir,
				Env.getLanguage(ctx), "C_UOM_ID", 0, false,
				"C_UOM.AD_Client_ID = @#AD_Client_ID@ and C_UOM.IsActive='Y' and C_UOM.C_UOM_ID "
						+ "IN (SELECT C_UOM_ID FROM C_UOM_Conversion where isActive = 'Y' )");

		fQtyOnOK = new WNumberEditor();
		fPriceActual = new WNumberEditor();
		fDiscount = new WNumberEditor();
		fTotalLines = new WNumberEditor();

		confirmPanel.getButton(ConfirmPanel.A_CANCEL).setVisible(false);

		productTable = new org.zkoss.zul.Listbox();
		productTable.setAttribute("org.zkoss.zul.nativebar", (Object) "true");
		productTable.setWidth("100%");
		productTable.setVflex(true);

		int i = 0;
		C_Line = i++;
		C_Product = i++;
		C_UOM = i++;
		C_Qty = i++;
		C_Price = i++;
		C_Discount = i++;
		C_Total = i++;
		C_IsUpdate = i++;

		Listhead head = new Listhead();
		head.appendChild(new Listheader(Msg.translate(ctx, MOrderLine.COLUMNNAME_Line)));
		head.appendChild(new Listheader(Msg.translate(ctx, MOrderLine.COLUMNNAME_M_Product_ID)));
		head.appendChild(new Listheader(Msg.translate(ctx, MOrderLine.COLUMNNAME_C_UOM_ID)));
		head.appendChild(new Listheader(Msg.translate(ctx, MOrderLine.COLUMNNAME_QtyOrdered)));
		head.appendChild(new Listheader(Msg.translate(ctx, MOrderLine.COLUMNNAME_PriceEntered)));
		head.appendChild(new Listheader(Msg.translate(ctx, MOrderLine.COLUMNNAME_Discount)));
		head.appendChild(new Listheader(Msg.translate(ctx, MOrderLine.COLUMNNAME_LineNetAmt)));

		head.appendChild(new Listheader(""));
		head.setParent(productTable);
		productTable.setItemRenderer(new ListitemRenderer<Object>() {

			@Override
			public void render(Listitem item, Object data, int index) throws Exception {
				@SuppressWarnings("unchecked")
				final Vector<Object> line = (Vector<Object>) data;

				btnDelete = new Button();
				btnDelete.setImage(ThemeManager.getThemeResource("images/Delete16.png"));
				btnDelete.setTooltiptext(Util.cleanAmp(Msg.getMsg(ctx, "Delete")));

				btnDelete.addEventListener(Events.ON_CLICK, new EventListener<Event>() {

					@Override
					public void onEvent(Event event) throws Exception {

						Button button = (Button) event.getTarget();
						Div div = (Div) button.getParent();
						Listcell listCell = (Listcell) div.getParent();
						Listitem item = (Listitem) listCell.getParent();
						KeyNamePair keyNamePair = (KeyNamePair) ((ListModelTable) productTable.getModel())
								.getValueAt(item.getIndex(), C_Line);

						MOrderLine ml = new MOrderLine(ctx, keyNamePair.getKey(), null);
						ml.deleteEx(true);
						loadLines();
					}
				});

				Boolean isUpdate = (Boolean) line.get(C_IsUpdate);
				final int line_ID = ((KeyNamePair) line.get(C_Line)).getKey();

				if (isUpdate) {
					fProduct = new WSearchEditor("M_Product_ID", true, false, true, lookupProduct);
					fUOM = new WTableDirEditor("M_Locator_ID", true, false, true, lookupUOM);

					WNumberEditor fQty = new WNumberEditor();
					WNumberEditor fPrice = new WNumberEditor();
					WNumberEditor fDisc = new WNumberEditor();
					WNumberEditor fTotal = new WNumberEditor();

					Listcell lstCell = new Listcell();
					KeyNamePair lineNo = (KeyNamePair) line.get(C_Line);
					lstCell.setLabel(lineNo.getName());
					lstCell.setParent(item);

					KeyNamePair product = (KeyNamePair) line.get(C_Product);
					Listcell lstCellProduct = new Listcell();
					if (product.getKey() != 0)
						fProduct.setValue(product.getKey());
					lstCellProduct.appendChild(fProduct.getComponent());
					lstCellProduct.setParent(item);
					fProduct.getComponent().setFocus(true);

					lstCell = new Listcell();
					KeyNamePair uom = (KeyNamePair) line.get(C_UOM);
					if (uom.getKey() != 0)
						fUOM.setValue(uom.getKey());
					fUOM.getComponent().setParent(lstCell);
					fUOM.getComponent().setWidth("100%");
					lstCell.setParent(item);

					lstCell = new Listcell();
					BigDecimal qty = new BigDecimal(line.get(C_Qty) + "");
					if (!qty.equals(Env.ZERO))
						fQty.setValue(qty);
					if (line_ID > 0)
						fQty.getComponent().setParent(lstCell);
					else
						fQtyOnOK.getComponent().setParent(lstCell);
					lstCell.setParent(item);

					lstCell = new Listcell();
					BigDecimal Price = new BigDecimal(line.get(C_Price) + "");
					if (!Price.equals(Env.ZERO))
						fPrice.setValue(Price);
					if (line_ID > 0)
						fPrice.getComponent().setParent(lstCell);
					else
						fPriceActual.getComponent().setParent(lstCell);
					lstCell.setParent(item);

					// Discount
					lstCell = new Listcell();
					BigDecimal Discount = new BigDecimal(line.get(C_Discount) + "");
					if (!Discount.equals(Env.ZERO))
						fDisc.setValue(Discount);
					if (line_ID > 0)
						fDisc.getComponent().setParent(lstCell);
					else
						fDiscount.getComponent().setParent(lstCell);
					lstCell.setParent(item);
					// Discount
					
					// Total
					lstCell = new Listcell();
					BigDecimal TotalLines = new BigDecimal(line.get(C_Total) + "");
					if (!TotalLines.equals(Env.ZERO))
						fTotal.setValue(TotalLines);
					if (line_ID > 0)
						fTotal.getComponent().setParent(lstCell);
					else
						fTotalLines.getComponent().setParent(lstCell);
					lstCell.setParent(item);
					fTotalLines.setReadWrite(false);
					// Total
					

					if (line_ID > 0) {
						lstCell = new Listcell();
						Div div = new Div();
						div.setStyle("text-align: center");
						div.appendChild(btnDelete);
						div.setParent(lstCell);
						lstCell.setParent(item);
					} 

					fProduct.addValueChangeListener(new ValueChangeListener() {

						@Override
						public void valueChange(ValueChangeEvent evt) {
							WSearchEditor fproduct = (WSearchEditor) evt.getSource();
							Listcell listCell = (Listcell) fproduct.getComponent().getParent();
							Listitem item = (Listitem) listCell.getParent();

							if (evt.getNewValue() != null) {
								int M_Product_ID = (Integer) evt.getNewValue();
								MProduct product = new MProduct(ctx, M_Product_ID, null);
								((ListModelTable) productTable.getModel()).setValueAt(
										new KeyNamePair(product.get_ID(), product.getValue()), item.getIndex(),
										C_Product);
								if (line_ID > 0) {
									MOrderLine movementLine = new MOrderLine(ctx, line_ID, null);
									movementLine.setM_Product_ID(M_Product_ID);
									movementLine.saveEx();
									loadLines();
								}

								fUOM.setValue(product.getC_UOM_ID());
								((ListModelTable) productTable.getModel()).setValueAt(
										new KeyNamePair(product.getC_UOM_ID(), product.getC_UOM().getName()),
										item.getIndex(), C_UOM);

							} else {
								((ListModelTable) productTable.getModel()).setValueAt(new KeyNamePair(0, ""),
										item.getIndex(), C_Product);
							}

						}
					});

					fUOM.addValueChangeListener(new ValueChangeListener() {

						@Override
						public void valueChange(ValueChangeEvent evt) {
							if (evt.getNewValue() != null) {
								int C_UOM_ID = (Integer) evt.getNewValue();
								if (line_ID > 0) {
									MOrderLine movementLine = new MOrderLine(ctx, line_ID, null);
									movementLine.set_ValueOfColumn("C_UOM_ID", C_UOM_ID);
									movementLine.saveEx();
									loadLines();
								}

							}

						}
					});

					fQty.addValueChangeListener(new ValueChangeListener() {

						@Override
						public void valueChange(ValueChangeEvent evt) {
							if (evt.getNewValue() != null) {
								BigDecimal MovementQty = new BigDecimal(evt.getNewValue() + "");
								MOrderLine orderLine = new MOrderLine(ctx, line_ID, null);
								orderLine.setQtyEntered(MovementQty);
								orderLine.setQtyOrdered(MovementQty);
								orderLine.saveEx();
								loadLines();
							}

						}
					});

				} else {

					Listcell lstCell = new Listcell(((KeyNamePair) line.get(C_Line)).getName());
					lstCell.setParent(item);

					lstCell = new Listcell(line.get(C_Product) + "");
					lstCell.setParent(item);

					lstCell = new Listcell(line.get(C_UOM) + "");
					lstCell.setParent(item);

					lstCell = new Listcell(line.get(C_Qty) + "");
					lstCell.setParent(item);

					lstCell = new Listcell(line.get(C_Price) + "");
					lstCell.setParent(item);

					lstCell = new Listcell(line.get(C_Discount) + "");
					lstCell.setParent(item);
					
					lstCell = new Listcell(line.get(C_Total) + "");
					lstCell.setParent(item);

					lstCell = new Listcell();
					Div div = new Div();
					div.setStyle("text-align: center");
					div.appendChild(btnDelete);
					div.setParent(lstCell);
					lstCell.setParent(item);
				}
			}
		});
	}

	private void loadLines() {
		loadLinesDoubleClick(-1);
	}

	private void loadLinesDoubleClick(int index) {
		List<MOrderLine> lsLine = new Query(Env.getCtx(), MOrderLine.Table_Name, MOrder.Table_Name + "_ID = ?", null)
				.setParameters(order.get_ID()).setOrderBy(MOrderLine.COLUMNNAME_Line).list();

		linesData = new Vector<Vector<Object>>();
		Vector<Object> line;
		int i = 0;
		for (MOrderLine movementLine : lsLine) {

			line = new Vector<Object>();
			linesData.add(line);
			line.add(new KeyNamePair(movementLine.get_ID(), movementLine.getLine() + ""));
			line.add(new KeyNamePair(movementLine.getM_Product_ID(), movementLine.getM_Product().getName() + ""));
			if (movementLine.get_Value(MUOM.COLUMNNAME_C_UOM_ID) != null) {
				int C_UOM_ID = (Integer) movementLine.get_Value(MUOM.COLUMNNAME_C_UOM_ID);
				MUOM uom = new MUOM(ctx, C_UOM_ID, null);
				line.add(new KeyNamePair(uom.get_ID(), uom.getName() + ""));
			} else {
				line.add(new KeyNamePair(0, ""));
			}
			line.add(movementLine.getQtyEntered());
			line.add(movementLine.getPriceEntered());
			line.add(movementLine.getDiscount());
			line.add(movementLine.getLineNetAmt());
			line.add(i == index);
			i++;
		}

		line = new Vector<Object>();
		line.add(new KeyNamePair(0, ""));
		line.add(new KeyNamePair(0, ""));
		line.add(new KeyNamePair(0, ""));
		line.add(Env.ZERO);
		line.add(Env.ZERO);
		line.add(Env.ZERO);
		line.add(Env.ZERO);
		line.add(true);

	
		linesData.add(line);

		ListModelTable tableModel = new ListModelTable(linesData);
		tableModel.addTableModelListener(this);
		productTable.setModel(tableModel);

		Utils.setWidths(productTable.getListhead(), "5%", "40%", "10%", "10%", "10%", "10%", "10%", "5%");
	}

	private void saveNewLine() {

		if (fProduct.getValue() == null)
			throw new AdempiereException("Seleccione un producto");

		if (fUOM.getValue() == null)
			throw new AdempiereException(
					"Seleccione un valor en el campo: " + Msg.translate(Env.getCtx(), MUOM.COLUMNNAME_C_UOM_ID));

		if (fQtyOnOK.getValue() == null)
			throw new AdempiereException("Seleccione un valor en el campo: "
					+ Msg.translate(Env.getCtx(), MOrderLine.COLUMNNAME_QtyEntered));

		int M_Product_ID = (Integer) fProduct.getValue();
		int C_UOM_ID = (Integer) fUOM.getValue();
		BigDecimal qtyMove = (BigDecimal) fQtyOnOK.getValue();
		BigDecimal PriceEntered = (BigDecimal) fPriceActual.getValue();
		BigDecimal Discount = (BigDecimal) fDiscount.getValue();

		MOrderLine orderLine = new MOrderLine(order);
		orderLine.setM_Product_ID(M_Product_ID);
		orderLine.set_ValueOfColumn("C_UOM_ID", C_UOM_ID);
		orderLine.setQtyOrdered(qtyMove);
		orderLine.setQtyEntered(qtyMove);
		orderLine.setPriceActual(PriceEntered);
		orderLine.setPriceEntered(PriceEntered);
		orderLine.setDiscount(Discount);
		orderLine.saveEx();

	}

	public Mode getWindowMode() {
		return Mode.HIGHLIGHTED;
	}

	@Override
	public void tableChanged(WTableModelEvent event) {
		ListModelTable model = (ListModelTable) event.getModel();
		if (event.getColumn() == C_Product) {
			List<Component> children = productTable.getChildren();
			Listitem listItem = (Listitem) children.get(event.getIndex0() + 1);
			List<Component> children2 = listItem.getChildren();

			KeyNamePair product = (KeyNamePair) model.getValueAt(event.getIndex0(), C_Product);
			int M_Product_ID = product.getKey();
		}

		Object lt = model.getValueAt(event.getIndex0(), C_Product);
		if (!lt.toString().isEmpty()) {
			KeyNamePair product = (KeyNamePair) lt;
			int M_Product_ID = product.getKey();

			List<Component> children = productTable.getChildren();
			Listitem listItem = (Listitem) children.get(event.getIndex0() + 1);
			List<Component> children2 = listItem.getChildren();
		} else {
			List<Component> children = productTable.getChildren();
			Listitem listItem = (Listitem) children.get(event.getIndex0() + 1);
			List<Component> children2 = listItem.getChildren();
		}
	}

}
