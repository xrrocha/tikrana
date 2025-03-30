package tikrana.util

class FaultTest extends munit.FunSuite:
  test("Fault message is lazy".ignore):
      var called = false
      val fault = Fault:
          called = true
          "Error occured"
      assert(!called)
      assertEquals(fault.message, "Error occurred")
      assert(called)

  test("Fault message with cause"):
      val cause = new Exception("Cause")
      val fault = Fault("Error occurred", cause)
      assertEquals(fault.message, "Error occurred")
      assertEquals(fault.getCause, cause)
end FaultTest
