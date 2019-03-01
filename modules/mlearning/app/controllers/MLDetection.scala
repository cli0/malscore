package controllers.mlearning

import com.google.inject._
import play.api.mvc._
import workflow.mlearning.Peid
import workflow.mlearning.Yara
import workflow.mlearning.Virustotal
import workflow.mlearning.Peinfo

@Singleton
class MLDetection @Inject extends Controller {

  def applyML = Action(BodyParsers.parse.json) {implicit request =>

    val payload = request.body
    /*get sample identifier*/
    val id = (request.body \ "id").get.as[String]

    /*Apply Bisecting K-Means clustering and similarity calculation to the DB*/

    Peid.run_bkm_peid(id)

    Yara.run_bkm_yara(id)

    Virustotal.run_bkm_virustotal(id)

    Peinfo.run_bkm_peinfo(id)

    Ok(payload)
  }
}