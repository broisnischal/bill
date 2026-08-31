import { useMutation } from "@tanstack/react-query";
import { LoaderCircleIcon, SmartphoneIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Button } from "#/components/ui/button.tsx";
import { Input } from "#/components/ui/input.tsx";
import { Label } from "#/components/ui/label.tsx";
import { toast } from "#/components/ui/toast.tsx";
import { authClient } from "#/lib/auth/auth-client.ts";
import { normalizeNepaliMobile } from "#/lib/nepali/validators.ts";

/**
 * Signing in without an email address.
 *
 * A Nepali shopkeeper has a mobile number long before they have an email they check, so
 * the number is the account. Two ways in, and the second is the one most people will use:
 * either a code by SMS, or — if the app is already open on the phone in their pocket —
 * a code shown here that they type into it, which costs nothing and does not wait on a
 * network.
 */
export function PhoneSignIn() {
  const [mode, setMode] = useState<"phone" | "device">("phone");

  return (
    <div className="flex flex-col gap-4">
      <div className="flex gap-2 rounded-lg border border-border bg-muted/40 p-1">
        <TabButton active={mode === "phone"} onClick={() => setMode("phone")}>
          Get a code by SMS
        </TabButton>
        <TabButton active={mode === "device"} onClick={() => setMode("device")}>
          Use my phone
        </TabButton>
      </div>

      {mode === "phone" ? <SmsSignIn /> : <DeviceSignIn />}
    </div>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex-1 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
        active ? "bg-background text-foreground shadow-xs" : "text-muted-foreground"
      }`}
    >
      {children}
    </button>
  );
}

/** The ordinary route: a number, then the six digits that arrive by SMS. */
function SmsSignIn() {
  const [phone, setPhone] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [code, setCode] = useState("");

  const normalised = normalizeNepaliMobile(phone);

  const { mutate: send, isPending: sending } = useMutation({
    mutationFn: async () => {
      if (!normalised) throw new Error("Enter a 10-digit Nepali mobile number");
      const { error } = await authClient.phoneNumber.sendOtp({ phoneNumber: normalised });
      if (error) throw new Error(error.message ?? "Could not send the code");
      return normalised;
    },
    onSuccess: setSentTo,
    onError: (error: Error) => toast.add({ type: "error", description: error.message }),
  });

  const { mutate: verify, isPending: verifying } = useMutation({
    mutationFn: async () => {
      const { error } = await authClient.phoneNumber.verify({
        phoneNumber: sentTo!,
        code,
      });
      if (error) throw new Error(error.message ?? "That code did not work");
    },
    // Better Auth navigates on a successful sign-in, so there is nothing to do here.
    onError: (error: Error) => toast.add({ type: "error", description: error.message }),
  });

  if (!sentTo) {
    return (
      <form
        className="flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          send();
        }}
      >
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="phone">Mobile number</Label>
          <Input
            id="phone"
            inputMode="numeric"
            autoComplete="tel"
            placeholder="98XXXXXXXX"
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
          />
        </div>
        <Button type="submit" disabled={!normalised || sending}>
          {sending && <LoaderCircleIcon className="animate-spin" />}
          Send code
        </Button>
      </form>
    );
  }

  return (
    <form
      className="flex flex-col gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        verify();
      }}
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="otp">Code sent to {sentTo}</Label>
        <Input
          id="otp"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          value={code}
          onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))}
        />
      </div>
      <Button type="submit" disabled={code.length < 6 || verifying}>
        {verifying && <LoaderCircleIcon className="animate-spin" />}
        Verify
      </Button>
      <Button type="button" variant="ghost" onClick={() => setSentTo(null)}>
        Change number
      </Button>
    </form>
  );
}

/**
 * The route for someone already holding a signed-in phone.
 *
 * This browser shows a code and waits. Nothing here is a secret worth protecting — the
 * code lets nobody in on its own, because only a device that already has a session can
 * approve it.
 */
function DeviceSignIn() {
  const [request, setRequest] = useState<{ code: string; pollToken: string } | null>(null);
  const [expired, setExpired] = useState(false);
  const pollRef = useRef<number | null>(null);

  const { mutate: begin, isPending } = useMutation({
    mutationFn: async () => {
      const response = await fetch("/api/v1/web-login", { method: "POST" });
      if (!response.ok) throw new Error("Could not start a sign-in");
      return (await response.json()) as { code: string; pollToken: string };
    },
    onSuccess: (started) => {
      setExpired(false);
      setRequest(started);
    },
    onError: () => toast.add({ type: "error", description: "Could not start a sign-in" }),
  });

  useEffect(() => {
    if (!request) return;

    // Polling rather than a socket: the wait is measured in seconds and a socket for it
    // would be a second connection to keep alive for no benefit.
    const tick = async () => {
      const response = await fetch(
        `/api/v1/web-login?pollToken=${encodeURIComponent(request.pollToken)}`,
      );
      const body = (await response.json()) as { status?: string };

      if (body.status === "signed_in") {
        // The cookie is already set; a reload lands wherever the guard sends us.
        window.location.reload();
        return;
      }
      if (body.status === "expired" || body.status === "denied" || !response.ok) {
        setExpired(true);
        setRequest(null);
      }
    };

    pollRef.current = window.setInterval(tick, 2000);
    return () => {
      if (pollRef.current) window.clearInterval(pollRef.current);
    };
  }, [request]);

  if (!request) {
    return (
      <div className="flex flex-col gap-3">
        <p className="text-sm text-muted-foreground">
          Already signed in on your phone? Get a code here and type it into the app.
        </p>
        {expired && <p className="text-sm text-destructive">That code expired. Get another one.</p>}
        <Button onClick={() => begin()} disabled={isPending}>
          {isPending ? <LoaderCircleIcon className="animate-spin" /> : <SmartphoneIcon />}
          Show me a code
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-3 rounded-lg border border-border p-6">
      <p className="text-sm text-muted-foreground">Type this into Bill on your phone</p>
      <div className="font-mono text-4xl font-semibold tracking-[0.3em]">{request.code}</div>
      <p className="text-center text-xs text-muted-foreground">
        In the app: More → Settings → Sign in a computer.
        <br />
        This code works for five minutes.
      </p>
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <LoaderCircleIcon className="size-4 animate-spin" />
        Waiting for your phone…
      </div>
    </div>
  );
}
