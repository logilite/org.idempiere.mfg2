/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2007 e-Evolution,SC. All Rights Reserved.               *
 * Contributor(s): victor.perez@e-evolution.com http://www.e-evolution.com    *
 *****************************************************************************/

package org.adempiere.model.engines;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.acct.Doc;
import org.compiere.model.I_AD_WF_Node;
import org.compiere.model.I_M_CostElement;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MCharge;
import org.compiere.model.MConversionRate;
import org.compiere.model.MCost;
import org.compiere.model.MCostDetail;
import org.compiere.model.MCostElement;
import org.compiere.model.MProduct;
import org.compiere.model.MSysConfig;
import org.compiere.model.MTable;
import org.compiere.model.MTransaction;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.libero.model.MPPCostCollector;
import org.libero.model.MPPOrderCost;
import org.libero.model.RoutingService;
import org.libero.model.RoutingServiceFactory;
import org.libero.tables.I_PP_Order_BOMLine;

/**
 * Cost Engine
 * @author victor.perez@e-evolution.com http://www.e-evolution.com
 *
 */
public class CostEngine
{
	public static final String PP_COSTCOLLECTOR_COSTAMT_VARIATION_THRESOLD= "PP_COSTCOLLECTOR_COSTAMT_VARIATION_THRESOLD" ;
	/**	Logger							*/
	protected transient CLogger	log = CLogger.getCLogger (getClass());
	
	public String getCostingMethod()
	{
		return MCostElement.COSTINGMETHOD_StandardCosting;
	}
	
	public BigDecimal getResourceStandardCostRate(MPPCostCollector cc, int S_Resource_ID, CostDimension d, String trxName)
	{
		final MProduct resourceProduct = MProduct.forS_Resource_ID(Env.getCtx(), S_Resource_ID, null);
		return getProductStandardCostPrice(
				cc,
				resourceProduct,
				MAcctSchema.get(Env.getCtx(), d.getC_AcctSchema_ID()),
				MCostElement.get(Env.getCtx(), d.getM_CostElement_ID())
		);
	}
	
	public BigDecimal getResourceActualCostRate(MPPCostCollector cc, int S_Resource_ID, CostDimension d, String trxName)
	{
		if (S_Resource_ID <= 0)
			return Env.ZERO;
		final MProduct resourceProduct = MProduct.forS_Resource_ID(Env.getCtx(), S_Resource_ID, null);
		return getProductActualCostPrice(
				cc,
				resourceProduct,
				MAcctSchema.get(Env.getCtx(), d.getC_AcctSchema_ID()),
				MCostElement.get(Env.getCtx(), d.getM_CostElement_ID()),
				trxName
		);
	}
	
	public BigDecimal getProductActualCostPrice(MPPCostCollector cc, MProduct product, MAcctSchema as, MCostElement element, String trxName)
	{
		if(element.isStandardCosting() || !cc.isReceipt()) {
			CostDimension d = new CostDimension(product,
					as, as.getM_CostType_ID(),
					cc.getAD_Org_ID(), //AD_Org_ID,
					cc.getM_AttributeSetInstance_ID(), //M_ASI_ID,
					element.getM_CostElement_ID());
			MCost cost = d.toQuery(MCost.class, trxName).firstOnly();
			if(cost == null)
			{	if(!cc.isReceipt()) {
				//TODO implement case when 0 cost allow, or material is not purchased nor stocked.
				
				//1. when valid purchase line or invoice line with 0 cost
				String sql="SELECT Count(*) FROM M_CostDetail WHERE M_Product_ID=? AND Processed='Y' AND Amt=0.00 AND Qty > 0 AND (C_OrderLine_ID > 0 OR C_InvoiceLine_ID > 0)"
						+ " AND AD_Client_ID = ? ";
				ArrayList<Integer> list = new ArrayList<Integer>();
				list.add(product.getM_Product_ID());
				list.add(cc.getAD_Client_ID());
				
				String costingLevel = product.getCostingLevel(as);
				if(MAcctSchema.COSTINGLEVEL_BatchLot.equals(costingLevel))
				{
					sql = "SELECT Count(*) FROM M_CostDetail WHERE M_Product_ID=? AND Processed='Y' AND Amt=0.00 AND Qty > 0 AND (C_OrderLine_ID > 0 OR C_InvoiceLine_ID > 0)"
						+ " AND AD_Client_ID = ?  AND M_AttributeSetInstance_ID=?";
					list.add(cc.getM_AttributeSetInstance_ID());
				}
				
				int count = DB.getSQLValue(null,sql,list.toArray());
				if(count>0)
					return Env.ZERO;
				else
					throw new AdempiereException("@NotFound@ @M_Cost_ID@ - "+as+", "+element); 
				}else
					return Env.ZERO;
			}	
			BigDecimal price = cost.getCurrentCostPrice().add(cost.getCurrentCostPriceLL());
			return roundCost(price, as.getC_AcctSchema_ID());
		} else if(element.isAverageInvoice() || element.isAveragePO()) {
			List<MPPCostCollector> ccList = new Query(cc.getCtx(), MPPCostCollector.Table_Name,
					"PP_Order_ID = ? AND Posted != ? and PP_Cost_Collector_ID<>?", cc.get_TrxName())
							.setParameters(cc.getPP_Order_ID(), Doc.STATUS_Posted,cc.getPP_Cost_Collector_ID()).setOnlyActiveRecords(true)
							.list();
			if(ccList.size()>0) {
				StringBuffer sb = new StringBuffer();
				for(MPPCostCollector ci:ccList)
					sb.append(ci.getDocumentNo()).append(",");
				
				throw new AdempiereException("Following cost collector are not posted:"+ sb.toString()); 
			}
			BigDecimal price =getParentActualCostByCostType(as,element.get_ID(),cc);
			BigDecimal charges = DB.getSQLValueBD(trxName, "Select sum(Amt) from PP_Cost_Collector where PP_Order_ID=? and docStatus in ('CO','CL')", cc.getPP_Order_ID()); 
			if(charges!=null)
				price = price.add(charges);
			
			int precision = as.getCostingPrecision();
			return price.divide(cc.getMovementQty(),precision*2,RoundingMode.HALF_UP);
		}else {
			throw new AdempiereException("Costing method not supported - "+element.getCostingMethod());
		}
	}

	public static BigDecimal getParentActualCostByCostType(MAcctSchema accountSchema, int costElementId, MPPCostCollector costCollector) {
		StringBuffer whereClause = new StringBuffer()
		.append(MCostDetail.COLUMNNAME_C_AcctSchema_ID).append("=? AND ")
		.append(MCostDetail.COLUMNNAME_M_CostElement_ID + "=? AND ")
		.append(MCostDetail.COLUMNNAME_PP_Cost_Collector_ID)
		.append(" IN (SELECT PP_Cost_Collector_ID FROM PP_Cost_Collector cc WHERE cc.PP_Order_ID=? AND ")
		.append(" cc.CostCollectorType <> '").append(MPPCostCollector.COSTCOLLECTORTYPE_MaterialReceipt).append("')");

		List<MCostDetail> componentsIssue= new Query(costCollector.getCtx(), MCostDetail.Table_Name, whereClause.toString(), costCollector.get_TrxName())
				.setClient_ID()
				.setParameters(accountSchema.getC_AcctSchema_ID() , costElementId, costCollector.getPP_Order_ID())
				.list();

		AtomicReference<BigDecimal> actualCostReference = new AtomicReference<BigDecimal>(BigDecimal.ZERO);
		componentsIssue.stream().forEach( costDetail ->{
				actualCostReference.updateAndGet(cost -> cost.add(costDetail.getAmt()));
		});

		BigDecimal actualCost = actualCostReference.get();
		whereClause = new StringBuffer();
		whereClause
				.append(" EXISTS (SELECT 1 FROM PP_Cost_Collector cc ")
				.append(" WHERE PP_Cost_Collector_ID=M_Transaction.PP_Cost_Collector_ID AND cc.PP_Order_ID=? AND cc.M_Product_ID=? )");
		BigDecimal qtyDelivered = new Query(costCollector.getCtx(), MTransaction.Table_Name, whereClause.toString(), costCollector.get_TrxName())
				.setClient_ID()
				.setParameters(costCollector.getPP_Order_ID(), costCollector.getM_Product_ID())
				.sum(MTransaction.COLUMNNAME_MovementQty);

		if (actualCost == null)
			actualCost = Env.ZERO;

		if (qtyDelivered.signum() != 0)
			actualCost = actualCost.divide(qtyDelivered,
					accountSchema.getCostingPrecision(), RoundingMode.HALF_DOWN);

		BigDecimal rate = MConversionRate.getRate(
				costCollector.getC_Currency_ID(), costCollector.getC_Currency_ID(),
				costCollector.getDateAcct(), 0, //TODO implementing conversion type support on Cost collector
				costCollector.getAD_Client_ID(), costCollector.getAD_Org_ID());
		if (rate != null) {
			actualCost = actualCost.multiply(rate);
			actualCost = roundCost(actualCost,accountSchema.get_ID());
		}

		return actualCost;
	}
	
	public BigDecimal getProductStandardCostPrice(MPPCostCollector cc, MProduct product, MAcctSchema as, MCostElement element)
	{
		CostDimension d = new CostDimension(product,
				as, as.getM_CostType_ID(),
				cc.getAD_Org_ID(), //AD_Org_ID,
				cc.getM_AttributeSetInstance_ID(), //M_ASI_ID,
				element.getM_CostElement_ID());
		MPPOrderCost oc = d.toQuery(MPPOrderCost.class, MPPOrderCost.COLUMNNAME_PP_Order_ID+"=?",
				new Object[]{cc.getPP_Order_ID()},
				cc.get_TrxName())
		.firstOnly();
		if (oc == null)
		{
			return Env.ZERO;
		}
		BigDecimal costs = oc.getCurrentCostPrice().add(oc.getCurrentCostPriceLL());
		return roundCost(costs, as.getC_AcctSchema_ID());
	}
	
	protected  static BigDecimal roundCost(BigDecimal price, int C_AcctSchema_ID)
	{
		// Fix Cost Precision 
		int precision = MAcctSchema.get(Env.getCtx(), C_AcctSchema_ID).getCostingPrecision();
		BigDecimal priceRounded = price;
		if (priceRounded.scale() > precision)
		{
			priceRounded = priceRounded.setScale(precision, RoundingMode.HALF_UP);
		}
		return priceRounded;
	}

	public Collection<MCost> getByElement (MProduct product, MAcctSchema as, 
			int M_CostType_ID, int AD_Org_ID, int M_AttributeSetInstance_ID, int M_CostElement_ID)
	{
		CostDimension cd = new CostDimension(product, as, M_CostType_ID,
				AD_Org_ID, M_AttributeSetInstance_ID,
				M_CostElement_ID);
		return cd.toQuery(MCost.class, product.get_TrxName())
		.setOnlyActiveRecords(true)
		.list();
	}

	/**
	 * Get Cost Detail
	 * @param model Model Inventory Line
	 * @param as Account Schema
	 * @param M_CostElement_ID Cost Element
	 * @param M_AttributeSetInstance_ID
	 * @return MCostDetail 
	 */
	private MCostDetail getCostDetail(IDocumentLine model, MPPCostCollector cc ,MAcctSchema as, int M_CostElement_ID)
	{
		final String whereClause = "AD_Client_ID=? AND AD_Org_ID=?"
			+" AND "+model.get_TableName()+"_ID=?" 
			+" AND "+MCostDetail.COLUMNNAME_M_Product_ID+"=?"
			+" AND "+MCostDetail.COLUMNNAME_M_AttributeSetInstance_ID+"=?"
			+" AND "+MCostDetail.COLUMNNAME_C_AcctSchema_ID+"=?"
			//						+" AND "+MCostDetail.COLUMNNAME_M_CostType_ID+"=?"
			+" AND "+MCostDetail.COLUMNNAME_M_CostElement_ID+"=?";
		final Object[] params = new Object[]{
				cc.getAD_Client_ID(), 
				cc.getAD_Org_ID(), 
				model.get_ID(),
				cc.getM_Product_ID(),
				cc.getM_AttributeSetInstance_ID(),
				as.getC_AcctSchema_ID(),
				//as.getM_CostType_ID(), 
				M_CostElement_ID, 
		};
		return new Query(cc.getCtx(),MCostDetail.Table_Name, whereClause , cc.get_TrxName())
		.setParameters(params)
		.firstOnly();
	}	

	/**
	 * Create Cost Detail (Material Issue, Material Receipt)
	 * @param model
	 * @param mtrx Material Transaction
	 */
	public void createCostDetail (IDocumentLine model , MPPCostCollector cc)
	{
		//TODO adding multiple cost type support
		for(MAcctSchema as : getAcctSchema(cc))
		{
			// Cost Detail
			final MProduct product = MProduct.get(cc.getCtx(), cc.getM_Product_ID());
			//final String costingMethod = product.getCostingMethod(as);
			// Check costing method
			//if (!getCostingMethod().equals(costingMethod))
			//{
			//	throw new AdempiereException("Costing method not supported - "+costingMethod);
			//}
			//TODO test for FG
			//TODO should we calculate cost for all elements? currently it creating for only product cost detail
			for (MCostElement element : getCostElements(cc.getCtx(),cc.get_TrxName()))
			{
				//
				//	Delete Unprocessed zero Differences
				deleteCostDetail(model, as, element.get_ID(), cc.getM_AttributeSetInstance_ID());
				//
				// Get Costs
				BigDecimal qty = cc.getMovementQty();
				final BigDecimal price = getProductActualCostPrice(cc, product, as, element, cc.get_TrxName());
				final BigDecimal amt = roundCost(price.multiply(qty), as.getC_AcctSchema_ID());
				if(!cc.isReceipt())
				{
					qty =  qty.negate();
				}
				//
				// Create / Update Cost Detail
				MCostDetail cd = getCostDetail(model, cc ,as, element.get_ID());
				boolean isCostDetailUpdated = false;
				if (cd == null)		//	createNew
				{	
					cd = new MCostDetail (as, cc.getAD_Org_ID(), 
							cc.getM_Product_ID(), cc.getM_AttributeSetInstance_ID(), 
							element.get_ID(),
							amt,
							qty,
							model.getDescription(),
							cc.get_TrxName());
					isCostDetailUpdated = true;
//					cd.setMovementDate(mtrx.getMovementDate());
//					if (cost != null)
//					{	
//						cd.setCurrentCostPrice(cost.getCurrentCostPrice());
//						cd.setCurrentCostPriceLL(cost.getCurrentCostPriceLL());
//					}
//					else
//					{
//						cd.setCurrentCostPrice(Env.ZERO);
//						cd.setCurrentCostPriceLL(Env.ZERO);
//					}
//					cd.setM_CostType_ID(as.getM_CostType_ID());
//					//cd.setCostingMethod(element.getCostingMethod());
//					cd.setM_Transaction_ID(mtrx.get_ID());
					if(model instanceof MPPCostCollector)
						cd.setPP_Cost_Collector_ID(model.get_ID());
				}
				else
				{ //TODO test for Receipt and Issue
					boolean isGoodToIgnoreDelta = false;
					if(qty.subtract(cd.getQty()).compareTo(Env.ZERO)==0) {
						CostDimension d = new CostDimension(product,
								as, as.getM_CostType_ID(),
								cc.getAD_Org_ID(), //AD_Org_ID,
								cc.getM_AttributeSetInstance_ID(), //M_ASI_ID,
								element.getM_CostElement_ID());
						MCost cost = d.toQuery(MCost.class, cc.get_TrxName()).firstOnly();
						BigDecimal costVarThresold = BigDecimal.valueOf(MSysConfig.getDoubleValue(PP_COSTCOLLECTOR_COSTAMT_VARIATION_THRESOLD, 0.0,cc.getAD_Client_ID()));
						if(cost!=null && cost.getCurrentQty().compareTo(Env.ZERO)==0 && amt.subtract(cd.getAmt()).abs().compareTo(costVarThresold)<0)
							isGoodToIgnoreDelta=true;
					}
					if(!isGoodToIgnoreDelta )
					{
						cd.setDeltaAmt(amt.subtract(cd.getAmt()));
						cd.setDeltaQty(qty.subtract(cd.getQty()));
						if (cd.isDelta())
						{
							cd.setProcessed(false);
							cd.setAmt(amt);
							cd.setQty(qty);
						}
						isCostDetailUpdated = true;
					}
				}
				if(isCostDetailUpdated)
				{
					cd.saveEx();
					processCostDetail(cd);
				}
				log.config("" + cd);
			} // for ELements	
		} // Account Schema 			
	}

	private int deleteCostDetail(IDocumentLine model, MAcctSchema as ,int M_CostElement_ID,
			int M_AttributeSetInstance_ID)
	{
		//	Delete Unprocessed zero Differences
		String sql = "DELETE " + MCostDetail.Table_Name
		+ " WHERE Processed='N' AND COALESCE(DeltaAmt,0)=0 AND COALESCE(DeltaQty,0)=0"
		+ " AND "+model.get_TableName()+"_ID=?" 
		+ " AND "+MCostDetail.COLUMNNAME_C_AcctSchema_ID+"=?" 
		+ " AND "+MCostDetail.COLUMNNAME_M_AttributeSetInstance_ID+"=?"
		//			+ " AND "+MCostDetail.COLUMNNAME_M_CostType_ID+"=?"
		+ " AND "+MCostDetail.COLUMNNAME_M_CostElement_ID+"=?";
		Object[] parameters = new Object[]{ model.get_ID(), 
				as.getC_AcctSchema_ID(), 
				M_AttributeSetInstance_ID,
				//as.getM_CostType_ID(),
				M_CostElement_ID};

		int no =DB.executeUpdateEx(sql,parameters, model.get_TrxName());
		if (no != 0)
			log.config("Deleted #" + no);
		return no;
	}
	
	private void processCostDetail(MCostDetail cd)
	{
		if (!cd.isProcessed())
		{
				cd.process();
		}
	}

	public static boolean isActivityControlElement(I_M_CostElement element)
	{
		String costElementType = element.getCostElementType();
		return MCostElement.COSTELEMENTTYPE_Resource.equals(costElementType)
		|| MCostElement.COSTELEMENTTYPE_Overhead.equals(costElementType)
		|| MCostElement.COSTELEMENTTYPE_BurdenMOverhead.equals(costElementType);
	}

	private Collection<MCostElement> getCostElements(Properties ctx,String trxName)
	{		return new Query(ctx, MCostElement.Table_Name, null, trxName)
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.setOrderBy(MCostElement.COLUMNNAME_Created)
				.list();
	}
	
	private Collection<MAcctSchema> getAcctSchema(PO po)
	{
		int AD_Org_ID = po.getAD_Org_ID();
		MAcctSchema[] ass = MAcctSchema.getClientAcctSchema(po.getCtx(), po.getAD_Client_ID());
		ArrayList<MAcctSchema> list = new ArrayList<MAcctSchema>(ass.length);
		for (MAcctSchema as : ass)
		{
			if(!as.isSkipOrg(AD_Org_ID))
				list.add(as);
		}
		return list;
	}
	
	private MCostDetail getCostDetail(MPPCostCollector cc, int M_CostElement_ID)
	{
		final String whereClause = MCostDetail.COLUMNNAME_PP_Cost_Collector_ID+"=?"
		+" AND "+MCostDetail.COLUMNNAME_M_CostElement_ID+"=?";
		MCostDetail cd = new Query(cc.getCtx(), MCostDetail.Table_Name, whereClause, cc.get_TrxName())
		.setParameters(new Object[]{cc.getPP_Cost_Collector_ID(), M_CostElement_ID})
		.firstOnly();
		return cd;
	}

	private MPPCostCollector createVarianceCostCollector(MPPCostCollector cc, String CostCollectorType)
	{
		MPPCostCollector ccv = (MPPCostCollector) MTable.get(cc.getCtx(), MPPCostCollector.Table_Name).getPO(0,
				cc.get_TrxName());
		MPPCostCollector.copyValues(cc, ccv);
		ccv.setProcessing(false);
		ccv.setProcessed(false);
		ccv.setDocStatus(MPPCostCollector.STATUS_Drafted);
		ccv.setDocAction(MPPCostCollector.ACTION_Complete);
		ccv.setCostCollectorType(CostCollectorType);
		ccv.setDocumentNo(null); // reset
		ccv.saveEx();
		return ccv;
	}
	
	/**
	 * Create & Proce Cost Detail for Variances
	 * @param ccv
	 * @param amt
	 * @param qty
	 * @param cd (optional)
	 * @param product
	 * @param as
	 * @param element
	 * @return
	 */
	private MCostDetail createVarianceCostDetail(MPPCostCollector ccv, BigDecimal amt, BigDecimal qty,
			MCostDetail cd, MProduct product, MAcctSchema as, MCostElement element)
	{
		final MCostDetail cdv = new MCostDetail(ccv.getCtx(), 0, ccv.get_TrxName());
		if (cd != null)
		{
			MCostDetail.copyValues(cd, cdv);
			cdv.setProcessed(false);
		}
		if (product != null)
		{
			cdv.setM_Product_ID(product.getM_Product_ID());
			cdv.setM_AttributeSetInstance_ID(0);
		}
		if (as != null)
		{
			cdv.setC_AcctSchema_ID(as.getC_AcctSchema_ID());
		}
		if (element != null)
		{
			cdv.setM_CostElement_ID(element.getM_CostElement_ID());
		}
		//
		cdv.setPP_Cost_Collector_ID(ccv.getPP_Cost_Collector_ID());
		//TODO adding CostType support, Test and review
		cdv.setM_CostElement_ID(element.get_ID());
		cdv.setAmt(amt);
		cdv.setQty(qty);
		cdv.saveEx();
		processCostDetail(cdv);
		return cdv;
	}
	
	public void createActivityControl(MPPCostCollector cc)
	{
		if (!cc.isCostCollectorType(MPPCostCollector.COSTCOLLECTORTYPE_ActivityControl))
			return;
		//
		final MProduct product = MProduct.forS_Resource_ID(cc.getCtx(), cc.getS_Resource_ID(), null);
		final RoutingService routingService = RoutingServiceFactory.get().getRoutingService(cc.getAD_Client_ID());
		final BigDecimal qty = routingService.getResourceBaseValue(cc.getS_Resource_ID(), cc);
		for (MAcctSchema as : getAcctSchema(cc))
		{
			//TODO review this, may needs to consider multiple methods
			for (MCostElement element : MCostElement.getElements(cc.getCtx(), cc.get_TableName()))
			{
				if(!element.isActive())
					continue;
				
				if (!isActivityControlElement(element))
				{
					continue;
				}
				final CostDimension dimension = new CostDimension(product,
						as,
						as.getM_CostType_ID(),
						cc.getAD_Org_ID(), //AD_Org_ID,
						cc.getM_AttributeSetInstance_ID(), //M_ASI_ID
						element.getM_CostElement_ID());
				final BigDecimal price = getResourceActualCostRate(cc, cc.getS_Resource_ID(), dimension, cc.get_TrxName());
				BigDecimal costs = price.multiply(qty);
				if (costs.scale() > as.getCostingPrecision())
					costs = costs.setScale(as.getCostingPrecision(), RoundingMode.HALF_UP);
				//
				MCostDetail cd = new MCostDetail(as,
						cc.getAD_Org_ID(), //AD_Org_ID,
						dimension.getM_Product_ID(),
						cc.getM_AttributeSetInstance_ID(), // M_AttributeSetInstance_ID,
						element.getM_CostElement_ID(),
						costs.negate(),
						qty.negate(),
						"", // Description,
						cc.get_TrxName());
				cd.setPP_Cost_Collector_ID(cc.getPP_Cost_Collector_ID());
				cd.saveEx();
				processCostDetail(cd);
			}
		}
	}
	
	public void createUsageVariances(MPPCostCollector ccuv)
	{//TODO test logic
		// Apply only for material Usage Variance
		if (!ccuv.isCostCollectorType(MPPCostCollector.COSTCOLLECTORTYPE_UsegeVariance))
		{
			throw new IllegalArgumentException("Cost Collector is not Material Usage Variance");
		}
		//
		final MProduct product;
		final BigDecimal qty;
		if(ccuv.get_ValueAsInt(MCharge.COLUMNNAME_C_Charge_ID)>0)
			return;
					
		if (ccuv.getPP_Order_BOMLine_ID() > 0)
		{
			product = MProduct.get(ccuv.getCtx(), ccuv.getM_Product_ID());
			qty = ccuv.getMovementQty();
		}
		else 
		{
			product = MProduct.forS_Resource_ID(ccuv.getCtx(), ccuv.getS_Resource_ID(), null);
			final RoutingService routingService = RoutingServiceFactory.get().getRoutingService(ccuv.getAD_Client_ID());
			qty = routingService.getResourceBaseValue(ccuv.getS_Resource_ID(), ccuv);
		}
		//
		for(MAcctSchema as : getAcctSchema(ccuv))
		{
			for (MCostElement element : getCostElements(ccuv.getCtx(),ccuv.get_TrxName()))
			{
					final BigDecimal price = getProductActualCostPrice(ccuv, product, as, element, ccuv.get_TrxName());
				final BigDecimal amt = roundCost(price.multiply(qty), as.getC_AcctSchema_ID());
				//
				// Create / Update Cost Detail
				if (amt.compareTo(Env.ZERO) != 0)
				{ //TODO adding multiple cost type support
					//TODO understand Usage Variance case and testing, Should amt always positive?
					createVarianceCostDetail(ccuv, amt, qty, null, // no
																	// original
																	// cost
																	// detail
							product, as, element);
				}
			} // for ELements	
		} // Account Schema 			
	}
	
	public void createRateVariances(MPPCostCollector costCollector)
	{ //TODO Understand Rate variance use case, test logic and fix
		final MProduct product;
		if (costCollector.isCostCollectorType(MPPCostCollector.COSTCOLLECTORTYPE_ActivityControl))
		{
			final I_AD_WF_Node node = costCollector.getPP_Order_Node().getAD_WF_Node();
			product = MProduct.forS_Resource_ID(costCollector.getCtx(), node.getS_Resource_ID(), null);
		}
		else if (costCollector.isCostCollectorType(MPPCostCollector.COSTCOLLECTORTYPE_ComponentIssue))
		{
			final I_PP_Order_BOMLine bomLine = costCollector.getPP_Order_BOMLine();
			product = MProduct.get(costCollector.getCtx(), bomLine.getM_Product_ID());
		}else if (MPPCostCollector.COSTCOLLECTORTYPE_RateVariance.equals(costCollector.getCostCollectorType())) {
			product =  MProduct.get(costCollector.getCtx(), costCollector.getM_Product_ID());
		}
		else
		{
			return;
		}
		
		MPPCostCollector costCollectorRateVariance = null; // Cost Collector - Rate Variance
		for (MAcctSchema as : getAcctSchema(costCollector))
		{
			for (MCostElement element : getCostElements(costCollector.getCtx(),costCollector.get_TrxName()))
			{
				final MCostDetail cd = getCostDetail(costCollector, element.getM_CostElement_ID());
				if (cd == null)
					continue;
				//
				final BigDecimal qty = cd.getQty();
				final BigDecimal priceStd = getProductStandardCostPrice(costCollector, product, as, element);
				final BigDecimal priceActual = getProductActualCostPrice(costCollector, product, as, element, costCollector.get_TrxName());
				final BigDecimal amtStd = roundCost(priceStd.multiply(qty), as.getC_AcctSchema_ID());
				final BigDecimal amtActual = roundCost(priceActual.multiply(qty), as.getC_AcctSchema_ID());
				if (amtStd.compareTo(amtActual) == 0)
					continue;
				//
				if (costCollectorRateVariance == null)
				{
					costCollectorRateVariance = createVarianceCostCollector(costCollector, MPPCostCollector.COSTCOLLECTORTYPE_RateVariance);
				}
				//
				createVarianceCostDetail(costCollectorRateVariance,
						amtActual.negate(), qty.negate(),
						cd, null, as, element);
				createVarianceCostDetail(costCollectorRateVariance,
						amtStd, qty,
						cd, null, as, element);
			}
		}
		//
		if (costCollectorRateVariance != null)
		{
			boolean ok = costCollectorRateVariance.processIt(MPPCostCollector.ACTION_Complete);
			costCollectorRateVariance.saveEx();
			if (!ok)
				throw new AdempiereException(costCollectorRateVariance.getProcessMsg());
		}
	}

	public void createMethodVariances(MPPCostCollector costCollector)
	{ //TODO Understand Method variance use case, test logic and fix
		if(costCollector.isCostCollectorType(MPPCostCollector.COSTCOLLECTORTYPE_MethodChangeVariance))
		{		
			for (MAcctSchema as : getAcctSchema(costCollector))
			{
				final MProduct product = costCollector.getM_Product();
				for (MCostElement element : getCostElements(costCollector.getCtx(),costCollector.get_TrxName()))
				{
					final BigDecimal qty = costCollector.getMovementQty();
					final BigDecimal priceStd = getProductActualCostPrice(costCollector, product, as, element, costCollector.get_TrxName());
					final BigDecimal amtStd = priceStd.multiply(qty);
					createVarianceCostDetail(costCollector,
							amtStd,qty,
							null, product, as, element);
				}
			}
			return;
		}
		//create the variance for routing	
		if (!costCollector.isCostCollectorType(MPPCostCollector.COSTCOLLECTORTYPE_ActivityControl))
			return;
		//
		final int std_resource_id = costCollector.getPP_Order_Node().getAD_WF_Node().getS_Resource_ID();
		final int actual_resource_id = costCollector.getS_Resource_ID();
		if (std_resource_id == actual_resource_id)
		{
			return;
		}
		//
		MPPCostCollector ccmv = null; // Cost Collector - Method Change Variance
		final RoutingService routingService = RoutingServiceFactory.get().getRoutingService(costCollector.getAD_Client_ID());
		for (MAcctSchema as : getAcctSchema(costCollector))
		{
			//TODO debug logic and find out which product's cost use
			final MProduct resourcePStd = MProduct.forS_Resource_ID(costCollector.getCtx(), std_resource_id, null); 
			final MProduct resourcePActual = MProduct.forS_Resource_ID(costCollector.getCtx(), actual_resource_id, null);
			for (MCostElement element : getCostElements(costCollector.getCtx(),costCollector.get_TrxName()))
			{
				final BigDecimal priceStd = getProductActualCostPrice(costCollector, resourcePStd, as, element, costCollector.get_TrxName());
				final BigDecimal priceActual = getProductActualCostPrice(costCollector, resourcePActual, as, element, costCollector.get_TrxName());
				if (priceStd.compareTo(priceActual) == 0)
				{
					continue;
				}
				//
				if (ccmv == null)
				{
					ccmv = createVarianceCostCollector(costCollector, MPPCostCollector.COSTCOLLECTORTYPE_MethodChangeVariance);
				}
				//
				final BigDecimal qty = routingService.getResourceBaseValue(costCollector.getS_Resource_ID(), costCollector);
				final BigDecimal amtStd = priceStd.multiply(qty); 
				final BigDecimal amtActual = priceActual.multiply(qty);
				//
				createVarianceCostDetail(ccmv,
						amtActual, qty,
						null, resourcePActual, as, element);
				createVarianceCostDetail(ccmv,
						amtStd.negate(), qty.negate(),
						null, resourcePStd, as, element);
			}
		}
		//
		if (ccmv != null)
		{
			boolean ok = ccmv.processIt(MPPCostCollector.ACTION_Complete);
			ccmv.saveEx();
			if (!ok)
				throw new AdempiereException(ccmv.getProcessMsg());
		}
	}

}
