package com.softwareag.jc.cumulocity.services.api

import android.os.Handler
import android.os.Looper
import com.softwareag.jc.common.api.Connection
import com.softwareag.jc.common.api.ConnectionRequest
import com.softwareag.jc.common.api.Method
import com.softwareag.jc.common.api.RequestResponder
import com.softwareag.jc.cumulocity.services.models.C8Y_MANAGED_OBJECTS_API
import com.softwareag.jc.cumulocity.services.models.C8Y_MANAGED_OBJECTS_EXT_API
import com.softwareag.jc.cumulocity.services.models.ManagedObject
import com.softwareag.jc.cumulocity.services.models.User
import org.json.JSONObject

/**
 * Principal access point for all Cumulocity data represented as [ManagedObject] and accessed via
 * API endpoint /inventory/managedObjects.
 *
 * You will need to use either the internal id of the managed object or reference it via an external
 * id that that has been pre-registered. The result is translated into a [ManagedObject] instance
 *
 * e.g.
 *  ```kotlin
 *  CumulocityConnectionFactory.connection(<tenant>, <instance e.g. cumulocity.com>).connect(<user>, <password>) { connection, responseInfo ->
 *
 *      ManagedObjectService(connection).get("12345") { result ->
 *
 *          if (status == 500) {
 *
 *              val failureReason: String? = results.reason
 *              ...
 *
 *          } else {
 *
 *              val device: ManagedObject = results.content
 *              ...
 *
 *          }
 *     }
 *  }
 *  ```
 *
 *  The results instance consists of the following properties
 *
 *  **status** - HTTP response code
 *
 *  **message** - HTTP response message, most often null unless the request failed
 *
 *  **headers** - HTTP response headers
 *
 *  **type** - ManagedObject
 *
 *  **content** - an instance of [ManagedObject].
 *
 *
 * Use the [ManagedObjectsService managedObjectsForQuery] method if you do need to find the managed
 * object via any other criteria.
 *
 * @property connection Connection referencing cumulocity tenant, instance and credentials to use
 * @constructor Creates a single use instance that can be used to launch a query, do not reuse the same
 * instance for multiple queries
 **/
class ManagedObjectService(connection: Connection<User>) : ConnectionRequest<User, ManagedObject>(connection) {

    private var _ref: String? = null
    private var _parent: String? = null
    private var _ext: String? = null
    private var _extType: String? = null

    /**
     * Fetch the managed object using the cumulocity internal id
     * @param id internal id as generated by cumulocity
     * @param responder callback function to which the RequestResponder<[ManagedObject]> instance
     * will be communicated to
     */
    fun get(id: String, responder: RequestResponder<ManagedObject>) {

        reset()

        _ref = id

        super.execute(responder)
    }

    /**
     * Fetch the managed object using the external id for the given type. Refer to the Device Management
     * console in cumulocity to find the details on an assets external ids and types.
     *
     * @param id external id registered in cumulocity
     * @param type the label of the external id registered in Cumulocity
     * @param responder callback function to which the RequestResponder<[ManagedObject]> instance
     * will be communicated to
     */
    fun get(id: String, type: String, responder: RequestResponder<ManagedObject>) {

        reset()

        if (type == "internal") {
            get(id, responder)
        } else {

            _ext = id
            _extType = type

            super.execute() {

                if (it.status in 200..210) {
                   var id: String? = it.content!!.properties[".id"] as String?

                    if (id != null) {
                        val handler = Handler(Looper.getMainLooper())
                        handler.post(Runnable {
                            ManagedObjectService(
                                connection as CumulocityConnectionFactory.CumulocityConnection
                            ).get(id!!, responder)
                        })
                    }
                    else {
                        responder(it)
                    }
                } else {
                    responder(it)
                }
            }
        }
    }

    /**
     * Adds the managed object to your cumulocity tenant. The internal id generated by Cumulocity will
     * added to the managed object reference, which will be referenced in the callback function.
     *
     * Note, not all elements of your managed object can be posted, refer to the [REST API Guide](https://cumulocity.com/guides/reference/inventory/#managed-object)
     * for more details
     *
     * @param obj a [ManagedObject] created locally for which the id attribute will be null
     * @param responder callback function to which the RequestResponder<[ManagedObject]> instance
     * will be communicated to. The ManagedObject is the same managed object as the obj param with
     * the id attribute updated to reflect the new instance in Cumulocity.
     */
    fun add(obj: ManagedObject, responder: RequestResponder<ManagedObject>) {

        reset()

        super.execute(Method.POST, "application/json", obj.toJSONString()) { r ->

            var location: String? = r.headers?.get("Location")?.get(0) ?: null

            if (location != null)
                obj.updateId(location.substring(location.lastIndexOf("/")+1))

            r.content = obj

            responder(r)
        }
    }

    /**
     * Updates the existing managed object. The id attribute must be provided in advance, it is
     * recommended that you only use this method with a managed object that was returned by the
     * aforementioned [add] method, [get] or query methods provided by the [ManagedObjectsService] class.
     *
     * Note, not all elements of your managed object can be posted, refer to the [REST API Guide](https://cumulocity.com/guides/reference/inventory/#managed-object)
     * for more details
     *
     * @param obj a [ManagedObject] for which the id attribute IS NOT null
     * @param responder callback function to which the RequestResponder<[ManagedObject]> instance
     * will be communicated to.
     */
    fun update(obj: ManagedObject, responder: RequestResponder<ManagedObject>) {

        reset()

        _ref = obj.id

        super.execute(Method.PUT, "application/json", obj.toJSONString(), responder)
    }

    /**
     * Associates the given managed object withe the other. This is most often used to add
     * a device 'c8y_Device' to a group 'c8y_DeviceGroup'
     *
     * @param child the internal id of the managed object to be assigned
     * @param parentId the internal id of parent managed object to which the child will be associated
     */
    fun assignToGroup(child: String, parentId: String, responder: RequestResponder<ManagedObject>?) {

        reset()

        _parent = parentId

        super.execute(
            Method.POST, "application/json", "{\n" +
                "    \"managedObject\" : {\n" +
                "        \"id\" : \"$child\"\n" +
                "    }\n" +
                "}", responder)
    }

    /**
     * Deletes the given managed object
     * @param obj a [ManagedObject] for which the id attribute IS NOT null
     */
    fun delete(obj: ManagedObject, responder: RequestResponder<ManagedObject>) {

        reset()

        _parent = null
        _ref = obj.id
        super.execute(Method.DELETE, responder)
    }

    protected override fun path(): String {

        return if (_ext != null) {
            C8Y_MANAGED_OBJECTS_EXT_API.replace("<TYPE>", _extType!!).replace("<EXTID>", _ext!!)
        } else if (_parent != null) {
            "$C8Y_MANAGED_OBJECTS_API/$_parent/childAssets"
        } else if (_ref != null) {
            "$C8Y_MANAGED_OBJECTS_API/$_ref"
        } else {
            C8Y_MANAGED_OBJECTS_API
        }
    }

    protected override fun response(response: String): ManagedObject {

        return ManagedObject(JSONObject(response))
    }

    private fun reset() {

        _parent = null
        _ref = null
        _ext = null
        _extType = null
    }
}