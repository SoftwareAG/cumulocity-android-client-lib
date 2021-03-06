package com.softwareag.jc.cumulocity.services.api

import com.softwareag.jc.common.api.ConnectionRequest
import com.softwareag.jc.common.api.Method
import com.softwareag.jc.common.api.RequestResponder
import com.softwareag.jc.common.kotlin.extensions.DateTools.Companion.dateToISO861
import com.softwareag.jc.cumulocity.services.models.ManagedObject
import com.softwareag.jc.cumulocity.services.models.Measurement
import com.softwareag.jc.cumulocity.services.models.User
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.*
import kotlin.collections.ArrayList

const val C8Y_MEASUREMENTS_API = "/measurement/measurements"
const val C8Y_MEASUREMENTS_API_GET_REF = "?source=<SOURCE>&revert=<REVERT>&dateFrom=<START>&dateTo=<END>&aggregationType=<AGGREGRATION_TYPE>&pageSize=500"
const val C8Y_MEASUREMENTS_API_GET_TYPE = "?source=<SOURCE>&type=<TYPE>&revert=<REVERT>&dateFrom=<START>&dateTo=<END>&aggregationType=<AGGREGRATION_TYPE>&pageSize=500"
const val C8Y_MEASUREMENTS_LIST = "measurements"

/**
 * Access point for retrieving and sending measurements to/from Cumulocity, data represented as [Measurement] and accessed via
 * API endpoint /measurement/measurements
 *
 * @property connection Connection referencing cumulocity tenant, instance and credentials to use
 * @constructor Creates a single use instance that cab be used to launch a query, do not reuse the same
 * instance for multiple queries
 **/
class MeasurementsService(override val connection: CumulocityConnectionFactory.CumulocityConnection) : ConnectionRequest<User, List<Measurement>>(connection) {

    /**
     * Specifies how the measurements results to be grouped, by minute, hour or 24 hours (DAILY)
     */
    enum class AggregateType(val value: Int) {
        DAILY(86400000),
        HOURLY(3600000),
        MINUTELY(60000)
    }

    private var _ref: String? = null
    private var _type: String? = null
    private var _series: String? = null
    private var _from: Date? = null
    private var _to: Date? = null
    private var _revert: Boolean = true

    private var _aggregateType: AggregateType? = null

    /**
     * Returns a list of [Measurement] generated by the asset for the given id parameter.
     *
     * @param id internal id of the [ManagedObject] that is the source of measurements to be returned
     * @param from Date and time to search from
     * @param to Data nd time to search upto (use Date() to specify up to now)
     * @param agggregrationType Limits the number of responses by grouping them into the specified time interval [AggregateType]
     * @param reverseDateOrder if false results are ordered date descending, specify true (default) for the opposite i.e.
     * the last measurement will be first in the list
     * @param responder callback function which will be called with the results
     */
    fun get(id: String, from: Date, to: Date, aggregrationType: AggregateType, reverseDateOrder: Boolean, responder: RequestResponder<List<Measurement>>) {

        _ref = id
        _from = from
        _to = to
        _aggregateType = aggregrationType
        _revert = reverseDateOrder

        super.execute(responder)
    }

    /**
     * Returns a list of [Measurement] generated by the asset for the given external id
     *
     * @param id external id of the [ManagedObject] that is the source of measurements to be returned
     * @param type the type of external id to be used
     * @param from Date and time to search from
     * @param to Data nd time to search upto (use Date() to specify up to now)
     * @param agggregrationType Limits the number of responses by grouping them into the specified time interval [AggregateType]
     * @param reverseDateOrder if false results are ordered date descending, specify true (default) for the opposite i.e.
     * the last measurement will be first in the list
     * @param responder callback function which will be called with the results
     */
    fun get(id: String, type: String, from: Date, to: Date, aggregrationType: AggregateType, reverseDateOrder: Boolean, responder: RequestResponder<List<Measurement>>) {

        _ref = id
        _from = from
        _to = to
        _type = type
        _aggregateType = aggregrationType
        _revert = reverseDateOrder

        super.execute(responder)
    }

    /**
     * Posts measurements to cumulocity
     */
    fun post(measurements: Array<Measurement>, responder: RequestResponder<List<Measurement>>) {

        super.execute(Method.POST, "application/vnd.com.nsn.cumulocity.measurementCollection+json", _toJsonList(measurements), responder)
    }

    protected override fun path(): String {

        if (_type != null) {
            return "$C8Y_MEASUREMENTS_API$C8Y_MEASUREMENTS_API_GET_TYPE"
                .replace("<SOURCE>", _ref!!)
                .replace("<START>", URLEncoder.encode(_from.dateToISO861(), "UTF8"))
                .replace("<END>", URLEncoder.encode(_to.dateToISO861(), "UTF8"))
                .replace("<TYPE>", _type!!)
                .replace("<AGGREGRATION_TYPE>", _aggregateType.toString())
                .replace("<REVERT>", _revert.toString())
        } else if (_ref != null) {
            return "$C8Y_MEASUREMENTS_API$C8Y_MEASUREMENTS_API_GET_REF"
                .replace("<SOURCE>", _ref!!)
                .replace("<START>", URLEncoder.encode(_from.dateToISO861(), "UTF8"))
                .replace("<END>", URLEncoder.encode(_to.dateToISO861(), "UTF8"))
                .replace("<AGGREGRATION_TYPE>", _aggregateType.toString())
                .replace("<REVERT>", _revert.toString())
        } else {
            return C8Y_MEASUREMENTS_API
        }
    }

    protected override fun response(response: String): List<Measurement> {

        val l: JSONArray = JSONObject(response).getJSONArray(C8Y_MEASUREMENTS_LIST)
        val ml: ArrayList<Measurement> = ArrayList()

        for (i in 0 until l.length()) {
            ml.add(Measurement(l.getJSONObject(i)))
        }

        return ml
    }

    private fun _toJsonList(l: Array<Measurement>): String {

        val out: StringBuilder = StringBuilder()

        out.append("{\"$C8Y_MEASUREMENTS_LIST\": [")

        l.forEach { m ->
            out.append(m.toJSONString()).append(",")
        }

        if (out.endsWith(","))
            out.deleteCharAt(out.length-1)


        out.append("]}")

        return out.toString()
    }
}