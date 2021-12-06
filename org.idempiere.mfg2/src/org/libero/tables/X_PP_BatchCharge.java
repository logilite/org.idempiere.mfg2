/******************************************************************************
 * Product: iDempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2012 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
/** Generated Model - DO NOT CHANGE */
package org.libero.tables;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Properties;
import org.compiere.model.*;
import org.compiere.util.Env;

/** Generated Model for PP_BatchCharge
 *  @author iDempiere (generated) 
 *  @version Release 7.1 - $Id$ */
public class X_PP_BatchCharge extends PO implements I_PP_BatchCharge, I_Persistent 
{

	/**
	 *
	 */
	private static final long serialVersionUID = 20211203L;

    /** Standard Constructor */
    public X_PP_BatchCharge (Properties ctx, int PP_BatchCharge_ID, String trxName)
    {
      super (ctx, PP_BatchCharge_ID, trxName);
      /** if (PP_BatchCharge_ID == 0)
        {
			setC_Charge_ID (0);
			setPP_BatchCharge_ID (0);
			setPP_MFGChargeMethod (null);
        } */
    }

    /** Load Constructor */
    public X_PP_BatchCharge (Properties ctx, ResultSet rs, String trxName)
    {
      super (ctx, rs, trxName);
    }

    /** AccessLevel
      * @return 3 - Client - Org 
      */
    protected int get_AccessLevel()
    {
      return accessLevel.intValue();
    }

    /** Load Meta Data */
    protected POInfo initPO (Properties ctx)
    {
      POInfo poi = POInfo.getPOInfo (ctx, Table_ID, get_TrxName());
      return poi;
    }

    public String toString()
    {
      StringBuffer sb = new StringBuffer ("X_PP_BatchCharge[")
        .append(get_ID()).append("]");
      return sb.toString();
    }

	public org.compiere.model.I_C_Charge getC_Charge() throws RuntimeException
    {
		return (org.compiere.model.I_C_Charge)MTable.get(getCtx(), org.compiere.model.I_C_Charge.Table_Name)
			.getPO(getC_Charge_ID(), get_TrxName());	}

	/** Set Charge.
		@param C_Charge_ID 
		Additional document charges
	  */
	public void setC_Charge_ID (int C_Charge_ID)
	{
		if (C_Charge_ID < 1) 
			set_Value (COLUMNNAME_C_Charge_ID, null);
		else 
			set_Value (COLUMNNAME_C_Charge_ID, Integer.valueOf(C_Charge_ID));
	}

	/** Get Charge.
		@return Additional document charges
	  */
	public int getC_Charge_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_C_Charge_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Batch Charge.
		@param PP_BatchCharge_ID Batch Charge	  */
	public void setPP_BatchCharge_ID (int PP_BatchCharge_ID)
	{
		if (PP_BatchCharge_ID < 1) 
			set_ValueNoCheck (COLUMNNAME_PP_BatchCharge_ID, null);
		else 
			set_ValueNoCheck (COLUMNNAME_PP_BatchCharge_ID, Integer.valueOf(PP_BatchCharge_ID));
	}

	/** Get Batch Charge.
		@return Batch Charge	  */
	public int getPP_BatchCharge_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_PP_BatchCharge_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set PP_BatchCharge_UU.
		@param PP_BatchCharge_UU PP_BatchCharge_UU	  */
	public void setPP_BatchCharge_UU (String PP_BatchCharge_UU)
	{
		set_Value (COLUMNNAME_PP_BatchCharge_UU, PP_BatchCharge_UU);
	}

	/** Get PP_BatchCharge_UU.
		@return PP_BatchCharge_UU	  */
	public String getPP_BatchCharge_UU () 
	{
		return (String)get_Value(COLUMNNAME_PP_BatchCharge_UU);
	}

	/** Batch Size = BTS */
	public static final String PP_MFGCHARGEMETHOD_BatchSize = "BTS";
	/** Fixed Amount = FXM */
	public static final String PP_MFGCHARGEMETHOD_FixedAmount = "FXM";
	/** Set MFG Charge Method.
		@param PP_MFGChargeMethod MFG Charge Method	  */
	public void setPP_MFGChargeMethod (String PP_MFGChargeMethod)
	{

		set_Value (COLUMNNAME_PP_MFGChargeMethod, PP_MFGChargeMethod);
	}

	/** Get MFG Charge Method.
		@return MFG Charge Method	  */
	public String getPP_MFGChargeMethod () 
	{
		return (String)get_Value(COLUMNNAME_PP_MFGChargeMethod);
	}

	public org.eevolution.model.I_PP_Product_BOM getPP_Product_BOM() throws RuntimeException
    {
		return (org.eevolution.model.I_PP_Product_BOM)MTable.get(getCtx(), org.eevolution.model.I_PP_Product_BOM.Table_Name)
			.getPO(getPP_Product_BOM_ID(), get_TrxName());	}

	/** Set BOM & Formula.
		@param PP_Product_BOM_ID 
		BOM & Formula
	  */
	public void setPP_Product_BOM_ID (int PP_Product_BOM_ID)
	{
		if (PP_Product_BOM_ID < 1) 
			set_Value (COLUMNNAME_PP_Product_BOM_ID, null);
		else 
			set_Value (COLUMNNAME_PP_Product_BOM_ID, Integer.valueOf(PP_Product_BOM_ID));
	}

	/** Get BOM & Formula.
		@return BOM & Formula
	  */
	public int getPP_Product_BOM_ID () 
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_PP_Product_BOM_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set Rate.
		@param Rate 
		Rate or Tax or Exchange
	  */
	public void setRate (BigDecimal Rate)
	{
		set_Value (COLUMNNAME_Rate, Rate);
	}

	/** Get Rate.
		@return Rate or Tax or Exchange
	  */
	public BigDecimal getRate () 
	{
		BigDecimal bd = (BigDecimal)get_Value(COLUMNNAME_Rate);
		if (bd == null)
			 return Env.ZERO;
		return bd;
	}
}