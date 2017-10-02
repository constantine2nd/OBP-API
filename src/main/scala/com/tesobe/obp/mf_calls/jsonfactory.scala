package com.tesobe.obp



case class MfAdminResponse(returnCode: String, messageText: Option[String])
case class ResponseStatus(callStatus: String, errorDesc: Option[String])
case class EsbHeaderResponse(esbRequestId: String, responseStatus: ResponseStatus)

case class SdrcMovil(
                     SDRC_MOVIL_BANK: String,
                     SDRC_MOVIL_SNIF: String, 
                     SDRC_MOVIL_CHN: String, 
                     SDRC_MOVIL_SUG: String
                    )
case class SdrcChn(
                    SDRC_CHN_BANK: String, //Bank
                    SDRC_CHN_SNIF: String, //Branch
                    SDRC_CHN_CHN: String,  //AccountNr
                    SDRC_CHN_SUG: String   //AccountType
                  )
case class SdrcHarshaot(
                         SDRC_MURSHE_MEIDA: String, //User can see Account
                         SDRC_MURSHE_PEULOT: String, //User can commit (internal) Transactions
                         SDRC_MURSHE_TZAD_G: String  //User can commit Transactions
                        )
case class SdrcCpGr(
                     SDRC_CP_KARTIS_ASHRAY: String,
                     SDRC_CP_KARTIS_ASHRAY_20: String,
                     SDRC_CP_SHEKIM_HOZRIM: String,
                     SDRC_CP_SHEKIM_NIMSHEHU: String,
                     SDRC_CP_MEMADIM: String,
                     SDRC_CP_FUSHE: String
                   )
case class SdrcLine(
                     SDRC_MOVIL: SdrcMovil,
                     SDRC_CHN: SdrcChn,
                     SDRC_HARSHAOT: SdrcHarshaot,
                     SDRC_SW_MOVIL: String,
                     SDRC_TAAGID: String,
                     SDRC_CP_GR: SdrcCpGr                     
                   )
case class SdrcLines(SDRC_LINE:SdrcLine)
case class SdrChn(SDR_CHN: List[SdrcLines])
case class SdrlLine(SDRL_LINE:List[SdrChn])

case class SdrmMovilRashi(
                         SDRM_MOVIL_RASHI_BANK: String,
                         SDRM_MOVIL_RASHI_SNIF: String,
                         SDRM_MOVIL_RASHI_CHN: String,
                         SDRM_MOVIL_RASHI_SUG: String
                         )

case class SdrManui(
                     SDRM_USERID: String,
                     SDRM_SHEM_PRATI: String,
                     SDRM_SHEM_MISHPACHA: String,
                     SDRM_TAR_LEIDA: String,
                     SDRM_MOVIL_RASHI: SdrmMovilRashi,
                     SDRM_DATE_LAST: String,
                     SDRM_TIME_LAST: String,
                     SDRM_NO_NISYONT_KOSHLIM: String,
                     SDRM_TA_PTICHA: String,
                     SDRM_ZEHUT: String,
                     SDRM_SUG_ZIHUY: String,
                     SDRM_CHASIMA_ZAD_G: String,
                     SDRM_SHEM_PRATI_ENG: String,
                     SDRM_SHEM_MISHPACHA_ENG: String
                   )
                  



case class SdrJoni(
                    esbHeaderResponse: EsbHeaderResponse, 
                    MFAdminResponse: MfAdminResponse, 
                    MFTOKEN: String,
                    SDR_MANUI: SdrManui,
                    SDR_LAK_SHEDER: SdrlLine
                  )
case class JoniMfUser(SDR_JONI: SdrJoni)

case class AccountPermissions(
                               canSee: Boolean,
                               canMakeInternalPayments: Boolean,
                               canMakeExternalPayments: Boolean
                            )

case class BasicBankAccount(accountNr: String,
                            branchNr: String,
                            accountType: String,
                            cbsToken: String,
                            accountPermissions: AccountPermissions)

case class FullBankAccount(
                            basicBankAccount: BasicBankAccount,
                            iban: String,
                            balance: String,
                            creditLimit: String
                          )

case class UserJSONV121(
                         id : String,
                         provider : String,
                         display_name : String
                       )

case class AmountOfMoneyJsonV121(
                                  currency : String,
                                  amount : String
                                )

case class AccountRoutingJsonV121(
                                   scheme: String,
                                   address: String
                                 )
case class AccountView(
                        id: String,
                        short_name: String,
                        is_public: Boolean
                      )
case class ModeratedCoreAccountJSON(
                                     id: String,
                                     bank_id: String,
                                     label: String,
                                     number: String,
                                     owners: List[UserJSONV121],
                                     views_available: List[AccountView],
                                     `type`: String,
                                     balance: AmountOfMoneyJsonV121,
                                     account_routing: AccountRoutingJsonV121
                                   )

case class Ta1TnuaBodedetContent(
                                  TA1_IND_KARTIS_ASHRAI: String,
                                  TA1_IND_HOR_KEVA: String,
                                  TA1_MAKOR_TNUA: String,
                                  TA1_TEUR_TNUA: String,
                                  TA1_TA_TNUA: String,
                                  TA1_SCHUM_TNUA: String,
                                  TA1_TA_ERECH: String,
                                  TA1_ASMACHTA: String
                                )

case class Ta1TnuaBodedet(
                           TA1_TNUA_BODEDET: Ta1TnuaBodedetContent
                         )
case class Ta1Tnuot(
                     TA1_PIRTEY_TNUA: List[Ta1TnuaBodedet]
                   )
case class Ta1ShetachLeSendNosaf(
                                  TA1_COUNTER: String,
                                  TA1_TNUOT: Ta1Tnuot
                                )
case class Ta1PirteiCheshbon(
                              TA1_SNIF: String,
                              TA1_SUG: String,
                              TA1_CHESHBON: String
                            )
case class Ta1tshuvatavlait1(
                              esbHeaderResponse: EsbHeaderResponse,
                              MFAdminResponse: MfAdminResponse,
                              TA1_PIRTEI_CHESHBON: Ta1PirteiCheshbon,
                              TA1_SHETACH_LE_SEND_NOSAF: Ta1ShetachLeSendNosaf
                            )

case class Nt1c3(
                 TA1TSHUVATAVLAIT1: Ta1tshuvatavlait1
                )

case class TnaTnuaBodedetContent(
                                  TNA_HOR_BIZ_IND: String,
                                  TNA_TEUR_PEULA: String,
                                  TNA_TA_ERECH: String,
                                  TNA_TA_BITZUA: String,
                                  TNA_ASMACHTA: String,
                                  TNA_SCHUM: String,
                                  TNA_ITRA: String,
                                  TNA_SEM_MAKOR: String,
                                  TNA_AMF_OR_NAFA: String
                                )

case class TnaTnuaBodedet(TNA_TNUA_BODEDET: TnaTnuaBodedetContent)

case class TnaTnuot(TNA_PIRTEY_TNUA: List[TnaTnuaBodedet])

case class TnaShetachLeSendNosaf(
                                  TNA_COUNTER: String,
                                  TNA_TNUOT: TnaTnuot
                                )

case class Tnatshuvatavlait1(
                              TNA_SHETACH_LE_SEND_NOSAF: TnaShetachLeSendNosaf
                            )


case class Nt1c4(
                  TNATSHUVATAVLAIT1: Tnatshuvatavlait1
                )

case class Tn2TnuaBodedetContent(
                                  TN2_IND_SEGMENT: String,
                                  TN2_HOR_BIZ_IND: String,
                                  TN2_TEUR_PEULA: String,
                                  TN2_SCHUM: String,
                                  TN2_TA_RIKUZ: String,
                                  TN2_ASMACTA: String,
                                  TN2_ITRA: String,
                                  TN2_TA_ERECH: String,
                                  TN2_DAF: String,
                                  TN2_NOSE_RASHI: String,
                                  TN2_SHNAT_DAF: String,
                                  TN2_SHURA: String,
                                  TN2_MIKUM_ZIKARON: String,
                                  TN2_SUG_ARCHAVA: String,
                                  TN2_SUG_PEULA: String,
                                  TN2_TA_IBUD: String,
                                  TN2_SADE_ARCHAVA: String,
                                  TN2_MIS_SIDURI: String,
                                  TN2_IND_CM: String,
                                  TN2_KOD_ARCHAVA: String
                                )

case class Tn2TnuaBodedet(TN2_TNUA_BODEDET: Tn2TnuaBodedetContent)

case class Tn2Tnuot(TN2_PIRTEY_TNUA: List[Tn2TnuaBodedet])

case class N2TshuvaTavlaitContent(TN2_COUNTER: String,
                           TN2_TNUOT: Tn2Tnuot
                          )

case class N2TshuvaTavlait(TN2_SHETACH_LE_SEND_NOSAF: N2TshuvaTavlaitContent)                       
                          

case class Nt1cT(TN2_TSHUVA_TAVLAIT: N2TshuvaTavlait)

case class O1recId(O1_REC_ID_I: String, O1_REC_ID_V: String)
case class O1contactRec(
                         O1_REC_ID: O1recId,
                         O1_STATE_CODE: String,
                         O1_STATE_NAME: String,
                         O1_REC_TYPE: String,
                         O1_MAIL_ADDRESS: String,
                         O1_TEL_NUM: String,
                         O1_TEL_AREA: String,
                         O1_TEL_COUNTRY: String,
                         O1_TEL_USE_TYPE_CODE: String,
                         O1_TEL_USE_TYPE_DESC: String,
                         O1_ORIG_SYSTEM_CODE: String,
                         O1_ORIG_SYSTEM_DESC: String,
                         O1_REC_MAIN: String,
                         O1_GET_MSG_FLAG: String,
                         O1_VERIFIED_FLAG: String,
                         O1_LINK_TO_SERVICE: String,
                         O1_CONFIRM_DATE: String
                       )
case class O1out1area1(
                        esbHeaderResponse: EsbHeaderResponse,
                        MFAdminResponse: MfAdminResponse,
                        O1_COUNT_REC_TEL: String,
                        O1_COUNT_REC_MAIL: String,
                        O1_CONTACT_STATUS: String,
                        O1_CONTACT_REC: List[O1contactRec]
)





case class Ntlv1(O1OUT1AREA_1:  O1out1area1)

case class P135Amalot(
                      P135_TEUR_AMALA: String,
                      P135_SCHUM_AMALA: String,
                      P135_SCHUM_STANDART_BANK: String,
                      P135_SCHUM_STANDART_MIGZAR: String,
                      P135_OFEN_CHISHUV_AMALA: String,
                      P135_SUG_HATAVA: String,
                      P135_KOD_HATAVA_BE_SHEUR: String,
                      P135_ACHUZ_TARIF: String,
                      P135_ACHUZ_ATZMI: String,
                      P135_YOM_GVIA: String,
                      P135_MIS_AMALA: String,
                      P135_SCHUM_HISAHON_AMLA: String
                     )

case class P135Bdikaout(esbHeaderResponse: EsbHeaderResponse,
                        MFAdminResponse: MfAdminResponse,
                        P135_TOKEN: String,
                        P135_AMALOT: P135Amalot
                       )

case class Ntbd1v135(P135_BDIKAOUT: P135Bdikaout)

case class P135Bdikaout2(esbHeaderResponse: EsbHeaderResponse,
                         MFAdminResponse: MfAdminResponse,
                         P135_SHAA_RISHUM: String, //Hour of transaction execution
                         P135_TARICH_BITZUA: String //Date of transaction execution YYYYMMDD
                         )

case class Ntbd2v135(P135_BDIKAOUT: P135Bdikaout2 )

case class Dfhplt1(esbHeaderResponse: EsbHeaderResponse,
                   MFAdminResponse: MfAdminResponse,
                  DFH_OPT: String)


case class Ntlv7(DFHPLT_1: Dfhplt1)

case class PPirteyKartis(P_MISPAR_KARTIS: String, // Card number
                         P_TOKEF_KARTIS: String, //Card Expiration Date
                         P_TIKRAT_KARTIS: String // Current limit to withdrawal from the card
                        )

case class PPratim(P_PIRTEY_KARTIS: List[PPirteyKartis])

case class PHeader(P_TIKRA_MAX_MUTAV: String) // Max. amount permitted to transfer

case class PeletNttfW(esbHeaderResponse: EsbHeaderResponse,
                      MFAdminResponse: MfAdminResponse,
                      P_HEADER: PHeader,
                      P_MONE_KARTISIM: String, // Number of cards linked to the account
                      P_PRATIM: PPratim
                     
                     )

case class NttfW(PELET_NTTF_W: PeletNttfW)



//From OBP-Scala-South-----------------------------------------------------

case class CounterPartySimple(name: Option[String],
                              id: Option[String]
                             )

case class ThisAccount(id: Option[String],
                       bank: Option[String]
                      )
case class Transaction(id: Option[String],
                       thisAccount: Option[ThisAccount],
                       counterparty: Option[CounterPartySimple],
                       userId: Option[String],
                       transactionChargePolicy: Option[String],
                       toCounterpartyBankRoutingScheme: Option[String],
                       toCounterpartyBankRoutingAddress: Option[String],
                       toCounterpartyName: Option[String],
                       transactionPostedDate: Option[String],
                       toCounterpartyRoutingAddress: Option[String],
                       fromAccountBankId: Option[String],
                       transactionRequestType: Option[String],
                       toCounterpartyCurrency: Option[String],
                       `type`: Option[String],
                       username: Option[String],
                       transactionChargeAmount: Option[String],
                       transactionDescription: Option[String],
                       transactionCurrency: Option[String],
                       fromAccountId: Option[String],
                       transactionAmount: Option[String],
                       toCounterpartyId: Option[String],
                       toCounterpartyRoutingScheme: Option[String],
                       fromAccountName: Option[String],
                       transactionChargeCurrency: Option[String],
                       transactionId: Option[String]
                      )
//End OBP-Scala-South-----------------------------------------------------

                   
