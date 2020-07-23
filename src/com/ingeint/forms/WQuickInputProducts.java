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
import org.compiere.model.MLocator;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MMovement;
import org.compiere.model.MMovementLine;
import org.compiere.model.MProduct;
import org.compiere.model.MStorageOnHand;
import org.compiere.model.MUOM;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
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
/*
 * autor: Sergio Oropeza sergioropeza88@gmail.com
 */
public class WQuickInputProducts extends CustomForm implements WTableModelListener {

	private static final long serialVersionUID = 1L;
	private Properties ctx = Env.getCtx();
	private WSearchEditor fProduct;
	private org.zkoss.zul.Listbox productTable;
	private ConfirmPanel confirmPanel = new ConfirmPanel(true);
	private WTableDirEditor fLocator;
	private WTableDirEditor fLocatorTo;
	private WNumberEditor fQtyOnOK;
	private MMovement movement;
	private int C_Product;
	private int C_Locator;
	private int C_LocatorTo;
	private int C_Qty;
	private int C_Select;
	private int C_Line;
	private Button btnDelete;
	private Vector<Vector<Object>> linesData;
	private int C_UOM;
	private int C_QtyOnHand;
	private WTableDirEditor fUOM;
	private int C_IsUpdate;
	private int M_Locator_ID;
	private int M_LocatorTo_ID;


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
	
	private void zkInit()  {
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
		fQtyOnOK.getComponent().addEventListener(Events.ON_OK, this);
		fQtyOnOK.getComponent().addEventListener(Events.ON_BLUR, this);
		productTable.addEventListener(Events.ON_DOUBLE_CLICK, this);
	}
	
	public void onEvent(Event e) throws Exception {	
		if (e.getTarget().equals(confirmPanel.getButton(ConfirmPanel.A_OK))){
			 detach();
		}else if (e.getTarget()==fQtyOnOK.getComponent().getDecimalbox()) {
			if (e.getName().equals(Events.ON_OK)){
				saveNewLine();
				loadLines();
				fQtyOnOK.setValue(null);
			}
			fProduct.getComponent().setFocus(true);	
		}else if (e.getTarget().equals(productTable)){
			if (e.getName().equals(Events.ON_DOUBLE_CLICK)){
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
	    movement = new MMovement(ctx, Record_ID, null);
	    
	    if (movement.getDocStatus() == DocAction.ACTION_Complete || 
	    		movement.getDocStatus() == DocAction.ACTION_Close)
	    	throw new AdempiereException("@Completed@");
	        
		MLookup lookupProduct = MLookupFactory.get(Env.getCtx(), getWindowNo(), 0, DisplayType.Search, Env.getLanguage(ctx),
				"M_Product_ID",0,false,"M_Product.AD_Client_ID = @#AD_Client_ID@ and M_Product.IsActive='Y'");

		MLookup lookupUOM = MLookupFactory.get(Env.getCtx(), getWindowNo(), 0, DisplayType.TableDir, Env.getLanguage(ctx),
				"C_UOM_ID",0,false,"C_UOM.AD_Client_ID = @#AD_Client_ID@ and C_UOM.IsActive='Y' and C_UOM.C_UOM_ID "
						+ "IN (SELECT C_UOM_ID FROM C_UOM_Conversion where isActive = 'Y' )");
		
		MLookup lookupLocator = MLookupFactory.get(Env.getCtx(), getWindowNo(), 2204, DisplayType.TableDir, Env.getLanguage(ctx),
				"M_Locator_ID",0,false,"AD_Client_ID = @#AD_Client_ID@ and IsActive='Y' AND M_Locator_ID NOT IN ("
						+ "SELECT M_ReserveLocator_ID FROM M_Warehouse wh where wh.AD_Client_ID = @#AD_Client_ID@) ");
		
		MLookup lookupLocatorTo = MLookupFactory.get(Env.getCtx(), getWindowNo(), 2204, DisplayType.TableDir, Env.getLanguage(ctx),
				"M_LocatorTo_ID",0,false,"AD_Client_ID = @#AD_Client_ID@ and IsActive='Y' AND M_Locator_ID NOT IN ("
					+ "SELECT M_ReserveLocator_ID FROM M_Warehouse wh where wh.AD_Client_ID = @#AD_Client_ID@) ");
		fQtyOnOK = new WNumberEditor(); 

		
		confirmPanel.getButton(ConfirmPanel.A_CANCEL).setVisible(false);
		
		productTable = new org.zkoss.zul.Listbox();	
		productTable.setAttribute("org.zkoss.zul.nativebar", (Object) "true");
		productTable.setWidth("100%");
		productTable.setVflex(true);
	
		int i =0;
		C_Line = i++;
		C_Product = i++;
		C_UOM = i++;
		C_QtyOnHand = i++;
		C_Locator = i++;
		C_LocatorTo = i++;
		C_Qty = i++;
		C_IsUpdate = i++;
		
		Listhead head = new Listhead();
		head.appendChild(new Listheader(Msg.translate(ctx, "Line")));
		head.appendChild(new Listheader(Msg.translate(ctx, "M_Product_ID")));
		head.appendChild(new Listheader(Msg.translate(ctx, "C_UOM_ID")));
		head.appendChild(new Listheader(Msg.translate(ctx, "QtyOnHand")));
		head.appendChild(new Listheader(Msg.translate(ctx, "M_Locator_ID")));
		head.appendChild(new Listheader(Msg.translate(ctx, "M_LocatorTo_ID")));
		head.appendChild(new Listheader(Msg.translate(ctx, "MovementQty")));
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
						
						Button button = (Button)event.getTarget();
						Div div = (Div)button.getParent();
						Listcell listCell = (Listcell)div.getParent();
						Listitem item = (Listitem)listCell.getParent();
						KeyNamePair keyNamePair = (KeyNamePair)((ListModelTable)productTable.getModel()).getValueAt(item.getIndex(),C_Line);
						
						MMovementLine ml = new MMovementLine(ctx,keyNamePair.getKey(), null );
						ml.deleteEx(true);
						loadLines();
					}
				});
				
				Boolean isUpdate = (Boolean)line.get(C_IsUpdate);
				final int line_ID = ((KeyNamePair)line.get(C_Line)).getKey();

	
				if (isUpdate) {	
					fProduct  = new WSearchEditor("M_Product_ID", true, false, true, lookupProduct);
					fUOM = new WTableDirEditor ("M_Locator_ID", true, false, true, lookupUOM);
					fLocator =  new WTableDirEditor ("M_Locator_ID", true, false, true, lookupLocator);
					fLocatorTo =  new WTableDirEditor ("M_LocatorTo_ID", true, false, true, lookupLocatorTo);	
					WNumberEditor fQty = new WNumberEditor(); 
					
					Listcell lstCell = new Listcell();
					KeyNamePair lineNo = (KeyNamePair)line.get(C_Line);
					lstCell.setLabel(lineNo.getName());
					lstCell.setParent(item);
					
					KeyNamePair product = (KeyNamePair)line.get(C_Product);
					Listcell lstCellProduct = new Listcell();
					if (product.getKey()!=0)
						fProduct.setValue(product.getKey());
					lstCellProduct.appendChild(fProduct.getComponent());
					lstCellProduct.setParent(item);
					fProduct.getComponent().setFocus(true);					
					
					lstCell = new Listcell();
					KeyNamePair uom = (KeyNamePair)line.get(C_UOM);
					if (uom.getKey()!=0)
						fUOM.setValue(uom.getKey());
					fUOM.getComponent().setParent(lstCell);
					fUOM.getComponent().setWidth("100%");
					lstCell.setParent(item);
							
					lstCell = new Listcell();
					lstCell.setParent(item);
					
					lstCell = new Listcell();
					KeyNamePair locator = (KeyNamePair)line.get(C_Locator);
					if (locator.getKey()!=0)
						fLocator.setValue(locator.getKey());

					fLocator.getComponent().setParent(lstCell);
					fLocator.getComponent().setWidth("100%");
					lstCell.setParent(item);
					
					lstCell = new Listcell();
					KeyNamePair locatorTo = (KeyNamePair)line.get(C_LocatorTo);
					if (locatorTo.getKey()!=0)
						fLocatorTo.setValue(locatorTo.getKey());
					fLocatorTo.getComponent().setParent(lstCell);
					fLocatorTo.getComponent().setWidth("100%");
					lstCell.setParent(item);
					
					lstCell = new Listcell();
					BigDecimal qty = new BigDecimal(line.get(C_Qty)+"");
					if (!qty.equals(Env.ZERO))
						fQty.setValue(qty);
					if (line_ID>0)
						fQty.getComponent().setParent(lstCell);
					else 
						fQtyOnOK.getComponent().setParent(lstCell);
					lstCell.setParent(item);
					
					if (line_ID>0) {
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
							WSearchEditor fproduct = (WSearchEditor)evt.getSource();
							Listcell listCell = (Listcell)fproduct.getComponent().getParent();
							Listitem item = (Listitem)listCell.getParent();
							
							if (evt.getNewValue()!=null) {
								int M_Product_ID = (Integer)evt.getNewValue();
								MProduct product = new MProduct(ctx,M_Product_ID , null);
								((ListModelTable)productTable.getModel()).setValueAt(new KeyNamePair(product.get_ID(), product.getValue()), item.getIndex(),C_Product);
								if (line_ID>0) {
									MMovementLine movementLine = new MMovementLine(ctx, line_ID, null);
									movementLine.setM_Product_ID(M_Product_ID);
									movementLine.saveEx();
									loadLines();
								}
								
								fUOM.setValue(product.getC_UOM_ID());
								((ListModelTable)productTable.getModel()).setValueAt(new KeyNamePair(product.getC_UOM_ID(), product.getC_UOM().getName()), item.getIndex(),C_UOM);
								
							}else {
								((ListModelTable)productTable.getModel()).setValueAt(new KeyNamePair(0, ""), item.getIndex(),C_Product);
							}
							
						}
					});
					
					fUOM.addValueChangeListener(new ValueChangeListener() {
						
						@Override
						public void valueChange(ValueChangeEvent evt) {
							if (evt.getNewValue()!=null) {
								int C_UOM_ID = (Integer)evt.getNewValue();
								if (line_ID>0) {
									MMovementLine movementLine = new MMovementLine(ctx, line_ID, null);
									movementLine.set_ValueOfColumn("C_UOM_ID", C_UOM_ID);
									movementLine.saveEx();
									loadLines();
								}
							
							}
							
						}
					});
					
					fLocator.addValueChangeListener(new ValueChangeListener() {
						
						@Override
						public void valueChange(ValueChangeEvent evt) {
							
							WTableDirEditor fLocator = (WTableDirEditor)evt.getSource();
							Listcell listCell = (Listcell)fLocator.getComponent().getParent();
							Listitem item = (Listitem)listCell.getParent();
							
							if (evt.getNewValue()!=null) {
								int M_Locator_ID = (Integer)evt.getNewValue();
								MLocator locator = new MLocator(ctx, M_Locator_ID, null);
								((ListModelTable)productTable.getModel()).setValueAt(new KeyNamePair(locator.get_ID(), locator.getValue()), item.getIndex(),C_Locator);
								if (line_ID>0) {
									MMovementLine movementLine = new MMovementLine(ctx, line_ID, null);
									movementLine.setM_Locator_ID(M_Locator_ID);
									movementLine.saveEx();
									loadLines();
								}
							
							}else {
								((ListModelTable)productTable.getModel()).setValueAt(new KeyNamePair(0, ""), item.getIndex(),C_Locator);
							}	
							
						}
					});
					
					fLocatorTo.addValueChangeListener(new ValueChangeListener() {
						
						@Override
						public void valueChange(ValueChangeEvent evt) {
							
							WTableDirEditor fLocator = (WTableDirEditor)evt.getSource();
							Listcell listCell = (Listcell)fLocator.getComponent().getParent();
							Listitem item = (Listitem)listCell.getParent();
							
							if (evt.getNewValue()!=null) {
								int M_LocatorTo_ID = (Integer)evt.getNewValue();
								MLocator locator = new MLocator(ctx, M_LocatorTo_ID, null);
								((ListModelTable)productTable.getModel()).setValueAt(new KeyNamePair(locator.get_ID(), locator.getValue()), item.getIndex(),C_LocatorTo);
								if (line_ID>0) {
									MMovementLine movementLine = new MMovementLine(ctx, line_ID, null);
									movementLine.setM_LocatorTo_ID(M_LocatorTo_ID);
									movementLine.saveEx();
									loadLines();
								}
							
							}else {
								((ListModelTable)productTable.getModel()).setValueAt(new KeyNamePair(0, ""), item.getIndex(),C_Locator);
							}	
						}
					});
					
					fQty.addValueChangeListener(new ValueChangeListener() {
						
						@Override
						public void valueChange(ValueChangeEvent evt) {
							if (evt.getNewValue()!=null) {
								BigDecimal MovementQty = new BigDecimal(evt.getNewValue()+"");
									MMovementLine movementLine = new MMovementLine(ctx, line_ID, null);
									movementLine.setMovementQty(MovementQty);
									movementLine.saveEx();
									loadLines();
							}
							
						}
					});
					
				}else {
														
					Listcell lstCell = new Listcell(((KeyNamePair)line.get(C_Line)).getName());
					lstCell.setParent(item);
					
					lstCell = new Listcell(line.get(C_Product)+"");
					lstCell.setParent(item);
					
					lstCell = new Listcell(line.get(C_UOM)+"");
					lstCell.setParent(item);
					
					lstCell = new Listcell(line.get(C_QtyOnHand)+"");
					lstCell.setParent(item);
					
					lstCell = new Listcell(line.get(C_Locator)+"");
					lstCell.setParent(item);
					
					lstCell = new Listcell(line.get(C_LocatorTo)+"");
					lstCell.setParent(item);
					
					lstCell = new Listcell(line.get(C_Qty)+"");
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
		List<MMovementLine> lsLine = new Query(Env.getCtx(),MMovementLine.Table_Name, MMovement.Table_Name+"_ID = ?" ,null).
				setParameters(movement.get_ID()).setOrderBy(MMovementLine.COLUMNNAME_Line).list();

		linesData = new Vector<Vector<Object>>();
		Vector<Object> line;
		int i = 0; 
		for (MMovementLine movementLine : lsLine) {
			
			if (movementLine.getM_Movement().getDocStatus().equals("CO"))
				throw new AdempiereException("@Completed@");			
			
			line = new Vector<Object>();
			linesData.add(line);
			line.add(new KeyNamePair(movementLine.get_ID(), movementLine.getLine()+""));
			line.add(new KeyNamePair(movementLine.getM_Product_ID(), movementLine.getM_Product().getName()+""));
			if (movementLine.get_Value(MUOM.COLUMNNAME_C_UOM_ID)!=null) {
				int C_UOM_ID = (Integer)movementLine.get_Value(MUOM.COLUMNNAME_C_UOM_ID);
				MUOM uom = new MUOM(ctx, C_UOM_ID, null);
				line.add(new KeyNamePair(uom.get_ID(), uom.getName()+""));
			}else 
				line.add(new KeyNamePair(0, ""));
			line.add("");
			line.add(new KeyNamePair(movementLine.getM_Locator_ID(), movementLine.getM_Locator().getValue()));
			line.add(new KeyNamePair(movementLine.getM_LocatorTo_ID(), movementLine.getM_LocatorTo().getValue()));
			line.add(movementLine.getMovementQty());
			line.add(i==index);
			i++;
		}
		line  = new Vector<Object>();
		line.add(new KeyNamePair(0, ""));
		line.add(new KeyNamePair(0, ""));
		line.add(new KeyNamePair(0, ""));
		line.add("");
		if (M_Locator_ID>0) {
			MLocator locator = new MLocator(ctx, M_Locator_ID, null);
			line.add(new KeyNamePair(locator.get_ID(), locator.getValue()));
		}else {
			line.add(new KeyNamePair(0, ""));
		}
		if (M_LocatorTo_ID>0) {
			MLocator locator = new MLocator(ctx, M_LocatorTo_ID, null);
			line.add(new KeyNamePair(locator.get_ID(), locator.getValue()));
		}else {
			line.add(new KeyNamePair(0, ""));
		}
		line.add(Env.ZERO);
		line.add(true);
		
		linesData.add(line);
		
		ListModelTable tableModel = new ListModelTable(linesData);
		tableModel.addTableModelListener(this);
		productTable.setModel(tableModel);
		
		Utils.setWidths(productTable.getListhead(), "5%", "40%", "10%", "10%", "10%", "10%", "10%", "5%");	
	}
	
	private void saveNewLine() {
		
		if (fProduct.getValue()==null)
			throw new AdempiereException("Seleccione un producto");
				
		if (fUOM.getValue()==null)
			throw new AdempiereException("Seleccione un valor en el campo: "+ Msg.translate(Env.getCtx(), MUOM.COLUMNNAME_C_UOM_ID));
		
		if (fLocator.getValue()==null)
			throw new AdempiereException("Seleccione un valor en el campo: "+ Msg.translate(Env.getCtx(), MMovementLine.COLUMNNAME_M_Locator_ID));
		
		if (fLocatorTo.getValue()==null)
			throw new AdempiereException("Seleccione un valor en el campo: "+ Msg.translate(Env.getCtx(), MMovementLine.COLUMNNAME_M_LocatorTo_ID));
		
		if (fQtyOnOK.getValue()==null)
			throw new AdempiereException("Seleccione un valor en el campo: "+ Msg.translate(Env.getCtx(), MMovementLine.COLUMNNAME_MovementQty));
		
		int M_Product_ID = (Integer)fProduct.getValue();
		M_Locator_ID = (Integer)fLocator.getValue();
		M_LocatorTo_ID = (Integer)fLocatorTo.getValue();
		int C_UOM_ID = (Integer)fUOM.getValue();
		BigDecimal qtyMove = (BigDecimal)fQtyOnOK.getValue();

		MMovementLine movementLine = new MMovementLine(movement);
		movementLine.setM_Product_ID(M_Product_ID);
		movementLine.setM_LocatorTo_ID(M_LocatorTo_ID);
		movementLine.setM_Locator_ID(M_Locator_ID);
		movementLine.setMovementQty(qtyMove);
		movementLine.set_ValueOfColumn("C_UOM_ID", C_UOM_ID);
		movementLine.saveEx();
		
		
	}
	
	public Mode getWindowMode() {
		return Mode.HIGHLIGHTED;
	}

	@Override
	public void tableChanged(WTableModelEvent event) {
		ListModelTable model = (ListModelTable) event.getModel();
		if (event.getColumn()==C_Product) {
			List<Component> children = productTable.getChildren();
			Listitem listItem = (Listitem) children.get(event.getIndex0()+1);
			List<Component> children2 = listItem.getChildren();
			Object lt = model.getValueAt(event.getIndex0(), C_Locator);
			if (!lt.toString().isEmpty()) {
				KeyNamePair locator = (KeyNamePair)lt;
				int M_Locator_ID = locator.getKey();
				KeyNamePair product = (KeyNamePair)model.getValueAt(event.getIndex0(), C_Product);
				int M_Product_ID = product.getKey();
				BigDecimal QtyOnHand = MStorageOnHand.getQtyOnHandForLocator(M_Product_ID, M_Locator_ID, 0, null);
				model.setValueAt(QtyOnHand, event.getIndex0(), C_QtyOnHand);
					
				Listcell cellQty = (Listcell)children2.get(C_QtyOnHand);
				cellQty.setLabel(QtyOnHand+"");
										
			}else {

				Listcell cellQty = (Listcell)children2.get(C_QtyOnHand);
				cellQty.setLabel("");
			}
			
		}if (event.getColumn()==C_Locator) {
			Object lt = model.getValueAt(event.getIndex0(), C_Product);
			if (!lt.toString().isEmpty()) {
				KeyNamePair product = (KeyNamePair)lt;
				KeyNamePair locator = (KeyNamePair)model.getValueAt(event.getIndex0(), C_Locator);
				int M_Locator_ID = locator.getKey();
				int M_Product_ID = product.getKey();
				BigDecimal QtyOnHand = MStorageOnHand.getQtyOnHandForLocator(M_Product_ID, M_Locator_ID, 0, null);
				model.setValueAt(QtyOnHand, event.getIndex0(), C_QtyOnHand);
				
				List<Component> children = productTable.getChildren();
				Listitem listItem = (Listitem) children.get(event.getIndex0()+1);
				List<Component> children2 = listItem.getChildren();
				Listcell cellQty = (Listcell)children2.get(C_QtyOnHand);
				cellQty.setLabel(QtyOnHand+"");
			}else {
				List<Component> children = productTable.getChildren();
				Listitem listItem = (Listitem) children.get(event.getIndex0()+1);
				List<Component> children2 = listItem.getChildren();
				Listcell cellQty = (Listcell)children2.get(C_QtyOnHand);
				cellQty.setLabel("");
			}
		}
		
	}
	
}