package tornadofx

import javafx.beans.DefaultProperty
import javafx.beans.binding.Bindings.createObjectBinding
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.css.PseudoClass
import javafx.event.EventTarget
import javafx.geometry.Orientation
import javafx.geometry.Orientation.HORIZONTAL
import javafx.geometry.Orientation.VERTICAL
import javafx.scene.Node
import javafx.scene.control.ButtonBar
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority.SOMETIMES
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.util.*
import java.util.concurrent.Callable

fun EventTarget.form(op: (Form.() -> Unit)? = null) = opcr(this, Form(), op)

fun EventTarget.fieldset(text: String? = null, icon: Node? = null, labelPosition: Orientation? = null, wrapWidth: Double? = null, op: (Fieldset.() -> Unit)? = null): Fieldset {
    val fieldset = Fieldset(text ?: "")
    if (wrapWidth != null) fieldset.wrapWidth = wrapWidth
    if (labelPosition != null) fieldset.labelPosition = labelPosition
    if (icon != null) fieldset.icon = icon
    opcr(this, fieldset, op)
    return fieldset
}

/**
 *  Creates a ButtonBarFiled with the given button order (refer to [javafx.scene.control.ButtonBar#buttonOrderProperty()] for more information about buttonOrder).
 */
fun EventTarget.buttonbar(buttonOrder: String? = null, forceLabelIndent: Boolean = true, op: (ButtonBar.() -> Unit)? = null): ButtonBarField {
    val field = ButtonBarField(buttonOrder, forceLabelIndent)
    opcr(this, field, null)
    op?.invoke(field.inputContainer)
    return field
}

/**
 * Create a field with the given text and operate on it.
 * @param text The label of the field
 * @param forceLabelIndent Indent the label even if it's empty, good for aligning buttons etc
 * @orientation Whether to create an HBox (HORIZONTAL) or a VBox (VERTICAL) container for the field content
 * @op Code that will run in the context of the content container (Either HBox or VBox per the orientation)
 *
 * @see buttonbar
 */
fun EventTarget.field(text: String? = null, orientation: Orientation = HORIZONTAL, forceLabelIndent: Boolean = false, op: (Pane.() -> Unit)? = null): Field {
    val field = Field(text ?: "", orientation, forceLabelIndent)
    opcr(this, field, null)
    op?.invoke(field.inputContainer)
    return field
}

open class Form : VBox() {
    init {
        addClass(Stylesheet.form)
    }

    internal fun labelContainerWidth(height: Double): Double
            = fieldsets.flatMap { it.fields }.map { it.labelContainer }.map { f -> f.prefWidth(-height) }.max() ?: 0.0

    internal val fieldsets = HashSet<Fieldset>()

    override fun getUserAgentStylesheet() =
            Form::class.java.getResource("form.css").toExternalForm()!!
}

@DefaultProperty("children")
class Fieldset(text: String? = null, labelPosition: Orientation = HORIZONTAL) : VBox() {
    var text by property<String>()
    fun textProperty() = getProperty(Fieldset::text)

    var inputGrow by property(SOMETIMES)
    fun inputGrowProperty() = getProperty(Fieldset::inputGrow)

    var labelPosition by property<Orientation>()
    fun labelPositionProperty() = getProperty(Fieldset::labelPosition)

    var wrapWidth by property<Number>()
    fun wrapWidthProperty() = getProperty(Fieldset::wrapWidth)

    var icon by property<Node>()
    fun iconProperty() = getProperty(Fieldset::icon)

    var legend by property<Label?>()
    fun legendProperty() = getProperty(Fieldset::legend)

    init {
        addClass(Stylesheet.fieldset)

        // Apply pseudo classes when orientation changes
        syncOrientationState()

        // Add legend label when text is populated
        textProperty().onChange { newValue -> if (!newValue.isNullOrBlank()) addLegend() }

        // Add legend when icon is populated
        iconProperty().onChange { newValue -> if (newValue != null) addLegend() }

        // Make sure input children gets the configured HBox.hgrow property
        syncHgrow()

        // Initial values
        this@Fieldset.labelPosition = labelPosition
        if (text != null) this@Fieldset.text = text

        // Register/deregister with parent Form
        parentProperty().addListener { _, oldParent, newParent ->
            ((oldParent as? Form) ?: oldParent?.findParentOfType(Form::class))?.fieldsets?.remove(this)
            ((newParent as? Form) ?: newParent?.findParentOfType(Form::class))?.fieldsets?.add(this)
        }
    }

    private fun syncHgrow() {
        children.addListener(ListChangeListener { c ->
            while (c.next()) {
                if (c.wasAdded()) {
                    for (added in c.addedSubList.asSequence().filterIsInstance<Field>()) {
                        // Configure hgrow for current children
                        for (it in added.inputContainer.children) {
                            configureHgrow(it)
                        }

                        // Add listener to support inputs added later
                        added.inputContainer.children.addListener(ListChangeListener { while (it.next()) if (it.wasAdded()) for (it in it.addedSubList) {
                            configureHgrow(it)
                        }
                        })
                    }
                }
            }
        })

        // Change HGrow for unconfigured children when inputGrow changes
        inputGrowProperty().onChange {
            for (field in children.asSequence().filterIsInstance<Field>()) {
                for (it in field.inputContainer.children) {
                    configureHgrow(it)
                }
            }
        }
    }

    private fun syncOrientationState() {
        labelPositionProperty().onChange { newValue ->
            if (newValue == HORIZONTAL) {
                pseudoClassStateChanged(VERTICAL_PSEUDOCLASS_STATE, false)
                pseudoClassStateChanged(HORIZONTAL_PSEUDOCLASS_STATE, true)
            } else {
                pseudoClassStateChanged(HORIZONTAL_PSEUDOCLASS_STATE, false)
                pseudoClassStateChanged(VERTICAL_PSEUDOCLASS_STATE, true)
            }
        }

        // Setup listeneres for wrapping
        wrapWidthProperty().onChange { newValue ->
            val responsiveOrientation = createObjectBinding<Orientation>(Callable {
                if (width < newValue?.toDouble() ?: 0.0) VERTICAL else HORIZONTAL
            }, widthProperty())

            if (labelPositionProperty().isBound)
                labelPositionProperty().unbind()

            labelPositionProperty().bind(responsiveOrientation)
        }
    }

    private fun addLegend() {
        if (legend == null) {
            legend = Label()
            legend!!.textProperty().bind(textProperty())
            legend!!.addClass(Stylesheet.legend)
            children.add(0, legend)
        }

        legend!!.graphic = icon
    }

    private fun configureHgrow(input: Node) {
        HBox.setHgrow(input, inputGrow)
    }

    val form: Form get() = findParentOfType(Form::class)!!

    internal val fields = HashSet<Field>()

    companion object {
        private val HORIZONTAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("horizontal")
        private val VERTICAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("vertical")
    }
}

/**
 * Make this Node (presumably an input element) the mnemonicTarget for the field label. When the label
 * of the field is activated, this input element will receive focus.
 */
fun Node.mnemonicTarget() {
    findParentOfType(Field::class)?.apply {
        label.isMnemonicParsing = true
        label.labelFor = this@mnemonicTarget
    }
}


@DefaultProperty("inputs")
class ButtonBarField(buttonOrder: String? = null, forceLabelIndent: Boolean = true) : AbstractField("", forceLabelIndent) {
    override val inputContainer = ButtonBar(buttonOrder)
    override val inputs: ObservableList<Node> = inputContainer.buttons

    init {
        inputContainer.addClass(Stylesheet.inputContainer)
        children.add(inputContainer)
    }
}

@DefaultProperty("inputs")
class Field(text: String? = null, orientation: Orientation = HORIZONTAL, forceLabelIndent: Boolean = false) : AbstractField(text, forceLabelIndent) {
    override val inputContainer = if (orientation == HORIZONTAL) HBox() else VBox()
    override val inputs: ObservableList<Node> = inputContainer.children

    init {
        inputContainer.addClass(Stylesheet.inputContainer)
        inputContainer.addPseudoClass(orientation.name.toLowerCase())
        children.add(inputContainer)

        // Register/deregister with parent Fieldset
        parentProperty().addListener { _, oldParent, newParent ->
            ((oldParent as? Fieldset) ?: oldParent?.findParentOfType(Fieldset::class))?.fields?.remove(this)
            ((newParent as? Fieldset) ?: newParent?.findParentOfType(Fieldset::class))?.fields?.add(this)
        }
    }
}

@DefaultProperty("inputs")
abstract class AbstractField(text: String? = null, val forceLabelIndent: Boolean = false) : Pane() {
    var text: String? by property(text)
    fun textProperty() = getProperty(Field::text)

    val label = Label()
    val labelContainer = HBox(label).apply { addClass(Stylesheet.labelContainer) }
    abstract val inputContainer: Region

    @Suppress("unused") // FXML Default Target
    abstract val inputs: ObservableList<Node>

    init {
        isFocusTraversable = false
        addClass(Stylesheet.field)
        label.textProperty().bind(textProperty())
        children.add(labelContainer)
    }

    val fieldset: Fieldset get() = findParentOfType(Fieldset::class)!!

    override fun computePrefHeight(width: Double): Double {
        val labelHasContent = forceLabelIndent || !text.isNullOrBlank()

        val labelHeight = if (labelHasContent) labelContainer.prefHeight(width) else 0.0
        val inputHeight = inputContainer.prefHeight(width)

        val insets = insets

        if (fieldset.labelPosition == HORIZONTAL)
            return Math.max(labelHeight, inputHeight) + insets.top + insets.bottom

        return labelHeight + inputHeight + insets.top + insets.bottom
    }

    override fun computePrefWidth(height: Double): Double {
        val fieldset = fieldset
        val labelHasContent = forceLabelIndent || !text.isNullOrBlank()

        val labelWidth = if (labelHasContent) fieldset.form.labelContainerWidth(height) else 0.0
        val inputWidth = inputContainer.prefWidth(height)

        val insets = insets

        if (fieldset.labelPosition == VERTICAL)
            return Math.max(labelWidth, inputWidth) + insets.left + insets.right

        return labelWidth + inputWidth + insets.left + insets.right
    }

    override fun computeMinHeight(width: Double) = computePrefHeight(width)

    override fun layoutChildren() {
        val fieldset = fieldset
        val labelHasContent = forceLabelIndent || !text.isNullOrBlank()

        val insets = insets
        val contentX = insets.left
        val contentY = insets.top
        val contentWidth = width - insets.left - insets.right
        val contentHeight = height - insets.top - insets.bottom

        val labelWidth = Math.min(contentWidth, fieldset.form.labelContainerWidth(height))

        if (fieldset.labelPosition == HORIZONTAL) {
            if (labelHasContent) {
                labelContainer.resizeRelocate(contentX, contentY, labelWidth, contentHeight)

                val inputX = contentX + labelWidth
                val inputWidth = contentWidth - labelWidth

                inputContainer.resizeRelocate(inputX, contentY, inputWidth, contentHeight)
            } else {
                inputContainer.resizeRelocate(contentX, contentY, contentWidth, contentHeight)
            }
        } else {
            if (labelHasContent) {
                val labelPrefHeight = labelContainer.prefHeight(width)
                val labelHeight = Math.min(labelPrefHeight, contentHeight)

                labelContainer.resizeRelocate(contentX, contentY, Math.min(labelWidth, contentWidth), labelHeight)

                val restHeight = contentHeight - labelHeight

                inputContainer.resizeRelocate(contentX, contentY + labelHeight, contentWidth, restHeight)
            } else {
                inputContainer.resizeRelocate(contentX, contentY, contentWidth, contentHeight)
            }
        }
    }

}