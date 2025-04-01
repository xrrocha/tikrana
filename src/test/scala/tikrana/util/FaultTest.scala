package tikrana.util

class FaultTest extends munit.FunSuite:
  test("Fault message is lazy"):
      var changed = false
      def changeIt(): Int =
        changed = true
        42
      val fault = Fault(s"Error occurred: ${changeIt()}")
      assert(!changed)
      assertEquals(fault.message, "Error occurred: 42")
      assert(changed)

  test("Fault message with cause"):
      val cause = new Exception("Cause")
      val fault = Fault("Error occurred", cause)
      assertEquals(fault.message, "Error occurred")
      assertEquals(fault.getCause, cause)
end FaultTest
